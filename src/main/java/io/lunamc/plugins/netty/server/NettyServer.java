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
import io.lunamc.platform.service.Shutdownable;
import io.lunamc.platform.service.Startable;
import io.lunamc.common.host.VirtualHostManager;
import io.lunamc.common.json.JsonMapper;
import io.lunamc.common.login.encryption.EncryptionFactory;
import io.lunamc.common.login.session.SessionClient;
import io.lunamc.common.server.Server;
import io.lunamc.plugins.netty.config.ServerConfiguration;
import io.lunamc.plugins.netty.handler.PlayHandlerFactory;
import io.lunamc.plugins.netty.netty.EventLoopGroupHolder;
import io.lunamc.plugins.netty.utils.NettyUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.Epoll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Objects;

public class NettyServer implements Server, Startable, Shutdownable {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyServer.class);
    private static final Marker MARKER_PERFORMANCE = MarkerFactory.getMarker("PERFORMANCE");
    private static final Marker MARKER_SERVER = MarkerFactory.getMarker("SERVER");

    private final ServiceRegistration<ServerConfiguration> config;
    private final ServiceRegistration<PlayHandlerFactory> playHandlerFactory;
    private final ServiceRegistration<EventLoopGroupHolder> eventLoopGroupHolder;
    private final ServiceRegistration<EncryptionFactory> encryptionFactory;
    private final ServiceRegistration<JsonMapper> jsonMapper;
    private final ServiceRegistration<VirtualHostManager> virtualHostManager;
    private final ServiceRegistration<SessionClient> sessionClient;
    private Channel channel;
    private boolean started;

    public NettyServer(ServiceRegistration<ServerConfiguration> config,
                       ServiceRegistration<PlayHandlerFactory> playHandlerFactory,
                       ServiceRegistration<EventLoopGroupHolder> eventLoopGroupHolder,
                       ServiceRegistration<EncryptionFactory> encryptionFactory,
                       ServiceRegistration<JsonMapper> jsonMapper,
                       ServiceRegistration<VirtualHostManager> virtualHostManager,
                       ServiceRegistration<SessionClient> sessionClient) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.playHandlerFactory = Objects.requireNonNull(playHandlerFactory, "playHandlerFactory must not be null");
        this.eventLoopGroupHolder = Objects.requireNonNull(eventLoopGroupHolder, "eventLoopGroupHolder must not be null");
        this.encryptionFactory = Objects.requireNonNull(encryptionFactory, "encryptionFactory must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.virtualHostManager = Objects.requireNonNull(virtualHostManager, "virtualHostManager must not be null");
        this.sessionClient = Objects.requireNonNull(sessionClient, "sessionClient must not be null");
    }

    @Override
    public synchronized void start() {
        if (started)
            throw new IllegalStateException("Already started");

        if (!Epoll.isAvailable())
            LOGGER.info(MARKER_PERFORMANCE, "Epoll will not be used (cause: {})", NettyUtils.getEpollUnavailabilityReason());
        else
            LOGGER.info(MARKER_PERFORMANCE, "Epoll is available and will be used");

        LOGGER.info(MARKER_SERVER, "Server is starting...");
        long timer = System.currentTimeMillis();
        ServerConfiguration config = this.config.requireInstance();
        EventLoopGroupHolder eventLoopGroupHolder = this.eventLoopGroupHolder.requireInstance();
        try {
            channel = new ServerBootstrap()
                    .group(eventLoopGroupHolder.getBossGroup(), eventLoopGroupHolder.getWorkerGroup())
                    .channel(NettyUtils.getServerSocketChannelClass())
                    .option(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new LunaChannelInitializer(
                            playHandlerFactory,
                            this.config,
                            encryptionFactory,
                            jsonMapper,
                            virtualHostManager,
                            sessionClient
                    ))
                    .bind(config.getPort())
                    .sync()
                    .channel();
            started = true;
        } catch (InterruptedException e) {
            LOGGER.warn(MARKER_SERVER, "Server startup interrupted");
        } catch (Throwable e) {
            LOGGER.error(MARKER_SERVER, "A exception was thrown during server startup", e);
        }
        timer = System.currentTimeMillis() - timer;
        if (started)
            LOGGER.info(MARKER_SERVER, "Server started (took {} ms)", timer);
        else
            LOGGER.error(MARKER_SERVER, "Server not started");
    }

    @Override
    public int getStartPriority() {
        return 0;
    }

    @Override
    public synchronized void shutdown() {
        if (!started)
            throw new IllegalStateException("Not started.");

        try {
            if (channel != null)
                channel.close().syncUninterruptibly();
        } finally {
            started = false;
        }
    }

    @Override
    public int getShutdownPriority() {
        return 90;
    }
}
