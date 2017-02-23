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
import io.lunamc.common.host.VirtualHostManager;
import io.lunamc.common.json.JsonMapper;
import io.lunamc.common.login.encryption.EncryptionFactory;
import io.lunamc.common.login.session.SessionClient;
import io.lunamc.common.network.DecidedConnection;
import io.lunamc.common.network.InitializedConnection;
import io.lunamc.plugins.netty.network.NettyDecidedConnection;
import io.lunamc.plugins.netty.network.NettyInitializedConnection;
import io.lunamc.plugins.netty.protocol.ProtocolException;
import io.lunamc.protocol.ProtocolUtils;
import io.lunamc.protocol.handler.PacketInboundHandlerAdapter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.Objects;

@ChannelHandler.Sharable
public class ProtocolHandshakeHandler extends PacketInboundHandlerAdapter {

    public static final String HANDLER_NAME = "handshake-handler";

    private final ServiceRegistration<EncryptionFactory> encryptionFactory;
    private final ServiceRegistration<JsonMapper> jsonMapper;
    private final ServiceRegistration<VirtualHostManager> virtualHostManager;
    private final ServiceRegistration<SessionClient> sessionClient;

    public ProtocolHandshakeHandler(ServiceRegistration<EncryptionFactory> encryptionFactory,
                                    ServiceRegistration<JsonMapper> jsonMapper,
                                    ServiceRegistration<VirtualHostManager> virtualHostManager,
                                    ServiceRegistration<SessionClient> sessionClient) {
        this.encryptionFactory = Objects.requireNonNull(encryptionFactory, "encryptionFactory must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.virtualHostManager = Objects.requireNonNull(virtualHostManager, "virtualHostManager must not be null");
        this.sessionClient = Objects.requireNonNull(sessionClient, "sessionClient must not be null");
    }

    @Override
    protected void handlePacket(ChannelHandlerContext ctx, int packetId, ByteBuf content) {
        switch (packetId) {
            case 0x00:
                // Handshake request
                handleHandshakeRequest(ctx, content);
                break;
            default:
                throw new ProtocolException("Unexpected packet " + Integer.toHexString(packetId) + " during handshake");
        }
    }

    protected void handleHandshakeRequest(ChannelHandlerContext ctx, ByteBuf content) {
        int protocolVersion = ProtocolUtils.readVarInt(content);
        String serverAddress = ProtocolUtils.readString(content);
        int port = content.readUnsignedShort();
        int nextState = ProtocolUtils.readVarInt(content);

        InitializedConnection connection = new NettyInitializedConnection(ctx.channel(), protocolVersion, serverAddress, port);
        VirtualHostManager virtualHostManager = this.virtualHostManager.requireInstance();
        VirtualHost virtualHost = virtualHostManager.matchHost(connection);
        if (virtualHost == null)
            virtualHost = virtualHostManager.getFallbackHost();
        if (virtualHost == null)
            throw new IllegalStateException("No fallback host found");

        DecidedConnection decidedConnection = new NettyDecidedConnection(ctx.channel(), protocolVersion, serverAddress, port, virtualHost);

        switch (nextState) {
            case 1:
                // Next state: STATUS
                setupStatus(ctx, decidedConnection);
                break;
            case 2:
                // Next state: LOGIN
                setupLogin(ctx, decidedConnection);
                break;
            default:
                throw new ProtocolException("Unexpected next state " + nextState);
        }
    }

    protected void setupStatus(ChannelHandlerContext ctx, DecidedConnection connection) {
        replaceHandler(ctx, ProtocolStatusHandler.HANDLER_NAME, new ProtocolStatusHandler(jsonMapper, connection));
    }

    protected void setupLogin(ChannelHandlerContext ctx, DecidedConnection connection) {
        replaceHandler(ctx, ProtocolLoginHandler.HANDLER_NAME, new ProtocolLoginHandler(encryptionFactory, sessionClient, connection));
    }

    private static void replaceHandler(ChannelHandlerContext ctx, String newName, ChannelHandler newHandler) {
        ctx.channel().pipeline().replace(HANDLER_NAME, newName, newHandler);
    }
}
