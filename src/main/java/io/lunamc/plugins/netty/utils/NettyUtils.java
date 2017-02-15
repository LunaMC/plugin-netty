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

package io.lunamc.plugins.netty.utils;

import io.lunamc.protocol.ChannelHandlerContextUtils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class NettyUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyUtils.class);

    private NettyUtils() {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " is a utility class and should not be constructed");
    }

    /**
     * Writes {@code msg} to {@code ctx}, flushes it and close the channel after the message is written.
     *
     * @param ctx The {@link ChannelHandlerContext} to which the message should be written
     * @param msg The message which should be written
     */
    public static void writeFlushAndClose(ChannelHandlerContext ctx, Object msg) {
        ChannelFuture future = ctx.writeAndFlush(msg);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    public static void debugChannelPipeline(ChannelHandlerContext ctx) {
        StringBuilder sb = new StringBuilder("Channel pipeline for ")
                .append(ChannelHandlerContextUtils.client(ctx))
                .append(':');
        for (Map.Entry<String, ChannelHandler> handlerEntry : ctx.pipeline()) {
            sb.append(System.lineSeparator())
                    .append('\t')
                    .append(handlerEntry.getKey())
                    .append(": ")
                    .append(handlerEntry.getValue());
        }
        LOGGER.debug(sb.toString());
    }

    public static String getEpollUnavailabilityReason() {
        Throwable cause = Epoll.unavailabilityCause();
        if (cause == null)
            return null;
        String message = getFirstMessage(cause);
        if (message == null)
            message = cause.getClass().getName();
        return message;
    }

    public static EventLoopGroup createEventLoopGroup() {
        return Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
    }

    public static EventLoopGroup createEventLoopGroup(int nThreads) {
        return Epoll.isAvailable() ? new EpollEventLoopGroup(nThreads) : new NioEventLoopGroup(nThreads);
    }

    public static Class<? extends ServerSocketChannel> getServerSocketChannelClass() {
        return Epoll.isAvailable() ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
    }

    public static Class<? extends SocketChannel> getSocketChannelClass() {
        return Epoll.isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class;
    }

    private static String getFirstMessage(Throwable throwable) {
        if (throwable == null)
            return null;
        String message = throwable.getMessage();
        if (message != null)
            return message;
        return getFirstMessage(throwable.getCause());
    }
}
