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
import io.lunamc.common.network.DecidedConnection;
import io.lunamc.common.network.InitializedConnection;
import io.lunamc.common.status.BetaStatusResponse;
import io.lunamc.common.status.LegacyStatusResponse;
import io.lunamc.plugins.netty.network.NettyConnection;
import io.lunamc.plugins.netty.network.NettyDecidedConnection;
import io.lunamc.plugins.netty.network.NettyInitializedConnection;
import io.lunamc.plugins.netty.utils.NettyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

import java.util.Objects;

@ChannelHandler.Sharable
public class LegacyPingHandler extends SimpleChannelInboundHandler<ByteBuf> {

    public static final String HANDLER_NAME = "legacy-ping";
    private static final String LEGACY_DELIMITER = "\b00\b00";

    private final ServiceRegistration<VirtualHostManager> virtualHostManager;

    public LegacyPingHandler(ServiceRegistration<VirtualHostManager> virtualHostManager) {
        super(ByteBuf.class, false);

        this.virtualHostManager = Objects.requireNonNull(virtualHostManager, "virtualHostManager must not be null");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        int b = msg.readByte();
        if (b == 0xfe) {
            try {
                handleLegacyPing(ctx, msg);
            } finally {
                msg.release();
            }
        } else {
            // No legacy ping so this handler is not required any longer
            ctx.channel().pipeline().remove(this);
            msg.resetReaderIndex();
            ctx.fireChannelRead(msg);
        }
    }

    private void handleLegacyPing(ChannelHandlerContext ctx, ByteBuf buffer) {
        if (!buffer.isReadable()) {
            // Beta 1.8 to 1.3
            sendBetaStatusResponse(ctx);
            return;
        }

        // Read "server list ping's payload"
        assert buffer.readByte() == 0x01;

        if (!buffer.isReadable()) {
            // 1.4
            sendLegacy14Response(ctx);
            return;
        }

        // 1.6

        // http://wiki.vg/index.php?title=Protocol&oldid=4779#Plugin_Message_.280xFA.29
        // Read "packet identifier for a plugin message"
        assert buffer.readByte() == 0xfa;

        // Read something which seems to be a plugin channel name
        assert readLegacyString(buffer).equals("MC|PingHost");

        // Read length of remaining data
        buffer.readShort();

        byte protocolVersion = buffer.readByte();
        String host = readLegacyString(buffer);
        int port = buffer.readInt();
        InitializedConnection connection = new NettyInitializedConnection(ctx.channel(), protocolVersion, host, port);
        VirtualHost virtualHost = virtualHostManager.requireInstance().matchHost(connection);
        DecidedConnection decidedConnection = new NettyDecidedConnection(ctx.channel(), protocolVersion, host, port, virtualHost);
        LegacyStatusResponse response = virtualHost.getStatusProvider(connection).createLegacy16StatusResponse(decidedConnection);

        ByteBuf output = ctx.alloc().buffer();
        output.writeByte(0xff);
        String dataString = composeLegacyStatusResponseData(response);
        writeLegacyString(output, dataString);
        NettyUtils.writeFlushAndClose(ctx, output);
    }

    private void sendLegacy14Response(ChannelHandlerContext ctx) {
        NettyConnection connection = new NettyConnection(ctx.channel());
        VirtualHost virtualHost = virtualHostManager.requireInstance().getFallbackHost();

        LegacyStatusResponse response = virtualHost.getStatusProvider(connection).createLegacy14StatusResponse(connection);
        ByteBuf output = ctx.alloc().buffer();
        output.writeByte(0xff);
        String dataString = composeLegacyStatusResponseData(response);
        writeLegacyString(output, dataString);
        NettyUtils.writeFlushAndClose(ctx, output);
    }

    private void sendBetaStatusResponse(ChannelHandlerContext ctx) {
        NettyConnection connection = new NettyConnection(ctx.channel());
        VirtualHost virtualHost = virtualHostManager.requireInstance().getFallbackHost();

        BetaStatusResponse response = virtualHost.getStatusProvider(connection).createBetaStatusResponse(connection);
        ByteBuf output = ctx.alloc().buffer();
        output.writeByte(0xff);
        String dataString = response.getMessageOfTheDay() + '§' +
                response.getCurrentPlayerCount() + '§' +
                response.getMaxPlayerCount();
        writeLegacyString(output, dataString);
        NettyUtils.writeFlushAndClose(ctx, output);
    }

    private static String readLegacyString(ByteBuf buffer) {
        int length = buffer.readShort();
        return buffer.readCharSequence(length, CharsetUtil.UTF_16BE).toString();
    }

    private static void writeLegacyString(ByteBuf buffer, String str) {
        byte[] data = str.getBytes(CharsetUtil.UTF_16BE);
        buffer.writeShort(data.length);
        buffer.writeBytes(data);
    }

    private static String composeLegacyStatusResponseData(LegacyStatusResponse response) {
        return "§1\0" + response.getProtocolVersion() + LEGACY_DELIMITER +
                response.getServerVersion() + LEGACY_DELIMITER +
                response.getMessageOfTheDay() + LEGACY_DELIMITER +
                response.getCurrentPlayerCount() + LEGACY_DELIMITER +
                response.getMaxPlayerCount();
    }
}
