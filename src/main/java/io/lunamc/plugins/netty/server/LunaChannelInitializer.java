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

package io.lunamc.plugins.netty.server;

import io.lunamc.platform.service.ServiceRegistration;
import io.lunamc.common.host.VirtualHostManager;
import io.lunamc.common.json.JsonMapper;
import io.lunamc.common.login.encryption.EncryptionFactory;
import io.lunamc.common.login.session.SessionClient;
import io.lunamc.plugins.netty.config.ServerConfiguration;
import io.lunamc.plugins.netty.handler.LegacyPingHandler;
import io.lunamc.plugins.netty.handler.OutboundExceptionHandler;
import io.lunamc.plugins.netty.handler.ProtocolHandshakeHandler;
import io.lunamc.plugins.netty.utils.LimitedLoggingHandler;
import io.lunamc.protocol.handler.LengthLimitedFrameDecoder;
import io.lunamc.protocol.handler.PacketLengthPrepender;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Objects;

public class LunaChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LunaChannelInitializer.class);
    private static final boolean DEBUG = Boolean.getBoolean("io.lunamc.plugins.netty.debugChannels");
    private static final String HANDLER_READ_TIMEOUT = "read-timeout";
    private static final String HANDLER_DEBUG = "debug";

    static {
        if (DEBUG)
            LOGGER.info("Channel debugging is enabled. This may decrease the performance.");
    }

    private final ServiceRegistration<ServerConfiguration> config;
    private final LegacyPingHandler legacyPingHandler;
    private final ProtocolHandshakeHandler handshakeHandler;

    public LunaChannelInitializer(ServiceRegistration<ServerConfiguration> config,
                                  ServiceRegistration<EncryptionFactory> encryptionFactory,
                                  ServiceRegistration<JsonMapper> jsonMapper,
                                  ServiceRegistration<VirtualHostManager> virtualHostManager,
                                  ServiceRegistration<SessionClient> sessionClient) {
        this.config = Objects.requireNonNull(config, "config must not be null");

        legacyPingHandler = new LegacyPingHandler(virtualHostManager);
        handshakeHandler = new ProtocolHandshakeHandler(encryptionFactory, jsonMapper, virtualHostManager, sessionClient);
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ServerConfiguration config = this.config.requireInstance();

        ch.pipeline()
                .addLast(HANDLER_READ_TIMEOUT, new ReadTimeoutHandler(config.getTimeout()))
                .addLast(LegacyPingHandler.HANDLER_NAME, legacyPingHandler)
                .addLast(LengthLimitedFrameDecoder.HANDLER_NAME, new LengthLimitedFrameDecoder())
                .addLast(PacketLengthPrepender.HANDLER_NAME, PacketLengthPrepender.INSTANCE)
                .addLast(OutboundExceptionHandler.HANDLER_NAME, OutboundExceptionHandler.INSTANCE)
                .addLast(ProtocolHandshakeHandler.HANDLER_NAME, handshakeHandler);

        if (DEBUG) {
            LimitedLoggingHandler loggingHandler = new LimitedLoggingHandler(LogLevel.DEBUG);
            ch.pipeline().addAfter(HANDLER_READ_TIMEOUT, HANDLER_DEBUG, loggingHandler);
            Logger logger = LoggerFactory.getLogger(loggingHandler.getClass());
            if (!logger.isDebugEnabled())
                LOGGER.warn("Logger {} must able to log {} to show channel debug messages", logger.getName(), Level.DEBUG);
        }
    }
}
