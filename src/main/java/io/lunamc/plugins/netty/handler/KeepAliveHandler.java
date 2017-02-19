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

import io.lunamc.protocol.ChannelHandlerContextUtils;
import io.lunamc.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class KeepAliveHandler extends ChannelHandlerAdapter {

    public static final String HANDLER_NAME = "keep-alive";
    private static final Logger LOGGER = LoggerFactory.getLogger(KeepAliveHandler.class);
    private static final int INTERVAL = 10;
    // 1 bytes for packet id (0x1F) and up to 5 bytes of payload
    private static final int PACKET_KEEP_ALIVE_SIZE = 6;
    private static final Random RANDOM = new Random();

    private ScheduledFuture<?> keepAliveFuture;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("Schedule keep alive for {}", ChannelHandlerContextUtils.client(ctx));
        keepAliveFuture = ctx.executor().scheduleAtFixedRate(() -> {
            if (ctx.channel().isActive())
                writeKeepAlive(ctx);
            else
                keepAliveFuture.cancel(true);
        }, INTERVAL, INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (keepAliveFuture != null) {
            keepAliveFuture.cancel(false);
            LOGGER.debug("Cancel keep alive schedule for {}", ChannelHandlerContextUtils.client(ctx));
        }
    }

    protected void writeKeepAlive(ChannelHandlerContext ctx) {
        LOGGER.debug("Write keep alive for {}", ChannelHandlerContextUtils.client(ctx));

        ByteBuf output = ctx.alloc().buffer(PACKET_KEEP_ALIVE_SIZE);
        // Write packet id (0x1f)
        ProtocolUtils.writeVarInt(output, 0x1f);
        // Write keep alive id
        ProtocolUtils.writeVarInt(output, RANDOM.nextInt());
        // Write packet
        ctx.writeAndFlush(output, ctx.voidPromise());
    }
}
