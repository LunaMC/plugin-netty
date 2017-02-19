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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class LimitedLoggingHandler extends LoggingHandler {

    private static final int MAX_BYTES = 16 * 16;

    public LimitedLoggingHandler() {
    }

    public LimitedLoggingHandler(LogLevel level) {
        super(level);
    }

    public LimitedLoggingHandler(Class<?> clazz) {
        super(clazz);
    }

    public LimitedLoggingHandler(Class<?> clazz, LogLevel level) {
        super(clazz, level);
    }

    public LimitedLoggingHandler(String name) {
        super(name);
    }

    public LimitedLoggingHandler(String name, LogLevel level) {
        super(name, level);
    }

    @Override
    protected String format(ChannelHandlerContext ctx, String eventName, Object arg) {
        if (arg instanceof ByteBuf) {
            ByteBuf buffer = (ByteBuf) arg;
            int readableBytes = buffer.readableBytes();
            if (readableBytes > MAX_BYTES) {
                return "Large buffer (" + readableBytes + " bytes). Print first " + MAX_BYTES + " bytes:" + System.lineSeparator() +
                        super.format(ctx, eventName, buffer.slice(0, MAX_BYTES)) + System.lineSeparator() + "and " +
                        (buffer.readableBytes() - MAX_BYTES) + " more bytes...";
            }
        }
        return super.format(ctx, eventName, arg);
    }
}
