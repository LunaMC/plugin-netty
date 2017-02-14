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
import io.lunamc.common.json.JsonMapper;
import io.lunamc.common.network.DecidedConnection;
import io.lunamc.common.status.StatusResponse;
import io.lunamc.plugins.netty.protocol.ProtocolException;
import io.lunamc.plugins.netty.utils.NettyUtils;
import io.lunamc.protocol.ProtocolUtils;
import io.lunamc.protocol.handler.PacketInboundHandlerAdapter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.Objects;

public class ProtocolStatusHandler extends PacketInboundHandlerAdapter {

    public static final String HANDLER_NAME = "status-handler";

    private final ServiceRegistration<JsonMapper> jsonMapper;
    private final DecidedConnection connection;

    public ProtocolStatusHandler(ServiceRegistration<JsonMapper> jsonMapper, DecidedConnection connection) {
        if (isSharable())
            throw new IllegalStateException("@Sharable not allowed here");

        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
    }

    @Override
    protected void handlePacket(ChannelHandlerContext ctx, int packetId, ByteBuf content) {
        switch (packetId) {
            case 0x00:
                // Status request
                handleStatusRequest(ctx);
                break;
            case 0x01:
                // Ping request
                handlePingRequest(ctx, content);
                break;
            default:
                throw new ProtocolException("Unexpected packet " + Integer.toHexString(packetId) + " during status");
        }
    }

    protected void handleStatusRequest(ChannelHandlerContext ctx) {
        StatusResponse response = connection.getVirtualHost().getStatusProvider(connection).createStatusResponse(connection);
        String serialized = jsonMapper.requireInstance().serialize(response);

        ByteBuf output = ctx.alloc().buffer();
        // Write packet id for status response)
        ProtocolUtils.writeVarInt(output, 0x00);
        // Write json status
        ProtocolUtils.writeString(output, serialized);
        ctx.writeAndFlush(output, ctx.voidPromise());
    }

    protected void handlePingRequest(ChannelHandlerContext ctx, ByteBuf content) {
        long payload = content.readLong();

        ByteBuf output = ctx.alloc().buffer();
        // Write packet id for ping request)
        ProtocolUtils.writeVarInt(output, 0x01);
        // Write ping payload
        output.writeLong(payload);
        NettyUtils.writeFlushAndClose(ctx, output);
    }
}
