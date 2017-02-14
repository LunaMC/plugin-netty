/*
 *  Copyright 2017 LunaMC.io
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.lunamc.plugins.netty.handler;

import io.lunamc.platform.service.ServiceRegistration;
import io.lunamc.common.host.VirtualHost;
import io.lunamc.common.login.encryption.Encryption;
import io.lunamc.common.login.encryption.EncryptionFactory;
import io.lunamc.common.login.session.Profile;
import io.lunamc.common.login.session.SessionClient;
import io.lunamc.common.login.session.StaticProfile;
import io.lunamc.common.network.AuthorizedConnection;
import io.lunamc.common.network.DecidedConnection;
import io.lunamc.plugins.netty.network.NettyAuthorizedConnection;
import io.lunamc.plugins.netty.protocol.ProtocolException;
import io.lunamc.protocol.ChannelHandlerContextUtils;
import io.lunamc.protocol.ProtocolUtils;
import io.lunamc.protocol.handler.LengthLimitedFrameDecoder;
import io.lunamc.protocol.handler.PacketInboundHandlerAdapter;
import io.lunamc.protocol.handler.PacketLengthPrepender;
import io.lunamc.protocol.handler.cipher.CipherDecoder;
import io.lunamc.protocol.handler.cipher.CipherEncoder;
import io.lunamc.protocol.handler.compression.PacketCompressor;
import io.lunamc.protocol.handler.compression.PacketDecompressor;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.zip.Deflater;

public class ProtocolLoginHandler extends PacketInboundHandlerAdapter {

    public static final String HANDLER_NAME = "login-handler";
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolLoginHandler.class);
    private static final Marker MARKER_COMPRESSION = MarkerFactory.getMarker("COMPRESSION");
    // ToDo: What should be the max length
    // Clarification: The -1 disables the warning of IntelliJ that an integer can never be greater than its max value
    private static final int MAX_SHARED_SECRET_LENGTH = Integer.MAX_VALUE - 1;
    private static final int MC_1_7_PROTOCOL_VERSION = 5;
    private static final String SERVER_ID;

    static {
        SERVER_ID = new Random()
                .ints('0', 'z' + 1)
                .filter(i -> (i >= '0' && i <= '9') || (i >= 'a' && i <= 'z')  || (i >= 'A' && i <= 'Z'))
                .limit(17)
                .mapToObj(i -> (char) i)
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }

    private final ServiceRegistration<PlayHandlerFactory> playHandlerFactory;
    private final ServiceRegistration<EncryptionFactory> encryptionFactory;
    private final ServiceRegistration<SessionClient> sessionClient;
    private final DecidedConnection connection;
    protected boolean authenticated;
    protected boolean encrypted;
    protected Encryption encryption;
    protected String loginData;
    protected SecretKey secret;

    public ProtocolLoginHandler(ServiceRegistration<PlayHandlerFactory> playHandlerFactory,
                                ServiceRegistration<EncryptionFactory> encryptionFactory,
                                ServiceRegistration<SessionClient> sessionClient,
                                DecidedConnection connection) {
        if (isSharable())
            throw new IllegalStateException("@Sharable not allowed here");

        this.encryptionFactory = Objects.requireNonNull(encryptionFactory, "encryptionFactory must not be null");
        this.sessionClient = Objects.requireNonNull(sessionClient, "sessionClient must not be null");
        this.playHandlerFactory = Objects.requireNonNull(playHandlerFactory, "playHandlerFactory must not be null");
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
    }

    @Override
    protected void handlePacket(ChannelHandlerContext ctx, int packetId, ByteBuf content) {
        switch (packetId) {
            case 0x00:
                // Login assertStarted
                handleLoginStart(ctx, content);
                break;
            case 0x01:
                // Encryption response
                handleEncryptionResponse(ctx, content);
                break;
        }
    }

    protected void handleLoginStart(ChannelHandlerContext ctx, ByteBuf content) {
        if (loginData != null)
            throw new ProtocolException("Login already started");
        loginData = ProtocolUtils.readString(content);
        authenticated = connection.getVirtualHost().isAuthenticated(connection);

        if (authenticated) {
            encryption = encryptionFactory.requireInstance().create(connection, loginData);

            ByteBuf output = ctx.alloc().buffer();
            // Packet id for encryption request (0x01)
            ProtocolUtils.writeVarInt(output, 0x01);
            // Server id (empty in > 1.7.x)
            ProtocolUtils.writeString(output, connection.getProtocolVersion() <= MC_1_7_PROTOCOL_VERSION ? SERVER_ID : "");
            // Public key length + data
            byte[] publicKey = encryption.getKeyPair().getPublic().getEncoded();
            ProtocolUtils.writeVarInt(output, publicKey.length);
            output.writeBytes(publicKey);
            // Verify token length + data
            byte[] verifyToken = encryption.getVerifyBytes();
            ProtocolUtils.writeVarInt(output, verifyToken.length);
            output.writeBytes(verifyToken);
            // Have a good journey!
            ctx.writeAndFlush(output, ctx.voidPromise());
        } else {
            authorize(ctx);
        }
    }

    protected void handleEncryptionResponse(ChannelHandlerContext ctx, ByteBuf content) {
        if (encryption == null)
            throw new ProtocolException("Expect 0x00 before 0x01");
        else if (encrypted)
            throw new ProtocolException("Already encrypted");

        // Read shared secret
        int sharedSecretLength = ProtocolUtils.readVarInt(content);
        if (sharedSecretLength > MAX_SHARED_SECRET_LENGTH)
            throw new ProtocolException("Shared secret exceed maximum size");
        byte[] sharedSecret = new byte[sharedSecretLength];
        content.readBytes(sharedSecret);

        // Check returned verify token
        int verifyBytesLength = ProtocolUtils.readVarInt(content);
        byte[] verifyBytes = new byte[verifyBytesLength];
        content.readBytes(verifyBytes);
        Cipher decryptCipher = createDecryptCipher();
        byte[] decryptedVerifyBytes;
        try {
            decryptedVerifyBytes = decryptCipher.doFinal(verifyBytes);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new ProtocolException("Invalid verify bytes", e);
        }
        if (!Arrays.equals(encryption.getVerifyBytes(), decryptedVerifyBytes))
            throw new ProtocolException("Verify token does not match");

        // Setup shared secret key
        byte[] decryptedSharedSecret;
        try {
            decryptedSharedSecret = decryptCipher.doFinal(sharedSecret);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new ProtocolException("Invalid shared secret", e);
        }
        secret = new SecretKeySpec(decryptedSharedSecret, "AES");

        ctx.channel().pipeline()
                .addBefore(PacketLengthPrepender.HANDLER_NAME, CipherEncoder.HANDLER_NAME, new CipherEncoder(secret))
                .addBefore(LengthLimitedFrameDecoder.HANDLER_NAME, CipherDecoder.HANDLER_NAME, new CipherDecoder(secret));
        encrypted = true;

        authorize(ctx);
    }

    private void authorize(ChannelHandlerContext ctx) {
        if (authenticated) {
            sessionClient.requireInstance().annotateJoin(loginData, connection.getProtocolVersion() <= MC_1_7_PROTOCOL_VERSION ? SERVER_ID : "", secret.getEncoded(), encryption.getKeyPair().getPublic())
                    .thenAccept(profile -> finalizeLogin(ctx, profile));
        } else {
            finalizeLogin(ctx, StaticProfile.createOfflineProfile(loginData));
        }
    }

    private void finalizeLogin(ChannelHandlerContext ctx, Profile profile) {
        VirtualHost.Compression compression = setupCompression(ctx, profile);

        ByteBuf output = ctx.alloc().buffer();
        // Packet id for login success (0x02)
        ProtocolUtils.writeVarInt(output, 0x02);
        // Write uuid as string
        ProtocolUtils.writeString(output, profile.getId());
        // Write username
        ProtocolUtils.writeString(output, profile.getName());
        // Bye bye(tes)
        ctx.writeAndFlush(output, ctx.voidPromise());

        AuthorizedConnection authorizedConnection = new NettyAuthorizedConnection(
                ctx.channel(),
                connection.getProtocolVersion(),
                connection.getServerAddress(),
                connection.getServerPort(),
                connection.getVirtualHost(),
                profile,
                compression
        );
        ChannelHandler playHandler = playHandlerFactory.requireInstance().createHandler(authorizedConnection);
        if (playHandler == null)
            throw new IllegalStateException("No play handler available");
        ctx.channel().pipeline().replace(HANDLER_NAME, PlayHandlerFactory.HANDLER_NAME, playHandler);
    }

    private VirtualHost.Compression setupCompression(ChannelHandlerContext ctx, Profile profile) {
        VirtualHost.Compression compression = connection.getVirtualHost().getCompression(connection, profile);
        if (compression != null) {
            int threshold = compression.getThreshold();
            if (threshold >= 0) {
                int level = compression.getCompressionLevel();
                if (level != Deflater.DEFAULT_COMPRESSION && (level < Deflater.NO_COMPRESSION || level > Deflater.BEST_COMPRESSION)) {
                    LOGGER.warn(MARKER_COMPRESSION, "Invalid compression level {} on connection {} {}", level, ChannelHandlerContextUtils.client(ctx), profile);
                } else if (level > Deflater.NO_COMPRESSION) {
                    LOGGER.debug(MARKER_COMPRESSION, "Compress packets for connection {} {} with threshold >= {} bytes and compression level {}", ChannelHandlerContextUtils.client(ctx), profile, threshold, level);

                    ByteBuf output = ctx.alloc().buffer();
                    // Packet id for set compression (0x03)
                    ProtocolUtils.writeVarInt(output, 0x03);
                    // Write threshold as VarInt
                    ProtocolUtils.writeVarInt(output, threshold);

                    ctx.channel().pipeline()
                            .addBefore(PacketLengthPrepender.HANDLER_NAME, PacketCompressor.HANDLER_NAME, new PacketCompressor(threshold, level))
                            .addBefore(PacketLengthPrepender.HANDLER_NAME, PacketDecompressor.HANDLER_NAME, new PacketDecompressor());

                    return compression;
                }
            }
        }

        return null;
    }

    private Cipher createDecryptCipher() {
        PrivateKey privateKey = encryption.getKeyPair().getPrivate();
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        return cipher;
    }
}
