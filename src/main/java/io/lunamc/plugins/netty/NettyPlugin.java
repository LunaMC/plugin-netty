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

package io.lunamc.plugins.netty;

import io.lunamc.platform.plugin.PluginAdapter;
import io.lunamc.platform.plugin.PluginContext;
import io.lunamc.platform.plugin.annotation.LunaPlugin;
import io.lunamc.platform.plugin.annotation.LunaPluginDependency;
import io.lunamc.platform.service.ServiceRegistration;
import io.lunamc.platform.service.ServiceRegistry;
import io.lunamc.common.host.VirtualHostManager;
import io.lunamc.common.json.JsonMapper;
import io.lunamc.common.login.encryption.EncryptionFactory;
import io.lunamc.common.login.session.SessionClient;
import io.lunamc.common.server.Server;
import io.lunamc.plugins.netty.config.ServerConfiguration;
import io.lunamc.plugins.netty.netty.DefaultGlobalEventExecutorController;
import io.lunamc.plugins.netty.netty.EventLoopGroupHolder;
import io.lunamc.plugins.netty.config.DefaultServerConfiguration;
import io.lunamc.plugins.netty.login.session.NettySessionClient;
import io.lunamc.plugins.netty.netty.DefaultEventLoopGroupHolder;
import io.lunamc.plugins.netty.netty.GlobalEventExecutorController;
import io.lunamc.plugins.netty.server.NettyServer;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

@LunaPlugin(
        id = "luna-netty",
        version = "0.0.1",
        pluginDependencies = {
                @LunaPluginDependency(id = "luna-common", versionExpression = "0.*")
        }
)
public class NettyPlugin extends PluginAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyPlugin.class);

    static {
        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
    }

    @Override
    public void initialize(PluginContext context) {
        ServiceRegistry serviceRegistry = context.getServiceRegistry();

        serviceRegistry.setService(GlobalEventExecutorController.class, new DefaultGlobalEventExecutorController());
        ServiceRegistration<JsonMapper> jsonMapper = serviceRegistry.getService(JsonMapper.class);
        ServiceRegistration<ServerConfiguration> config = serviceRegistry.setService(ServerConfiguration.class, loadConfiguration(context));
        ServiceRegistration<EventLoopGroupHolder> eventLoopGroupHolder = serviceRegistry.setService(EventLoopGroupHolder.class, new DefaultEventLoopGroupHolder());
        ServiceRegistration<SessionClient> sessionClient = serviceRegistry.setService(SessionClient.class, new NettySessionClient(
                eventLoopGroupHolder,
                jsonMapper
        ));
        serviceRegistry.setService(Server.class, new NettyServer(
                config,
                eventLoopGroupHolder,
                serviceRegistry.getService(EncryptionFactory.class),
                jsonMapper,
                serviceRegistry.getService(VirtualHostManager.class),
                sessionClient
        ));
    }

    private ServerConfiguration loadConfiguration(PluginContext context) {
        File configurationFile = new File(context.getDescription().getDataDirectory(), "server.xml");
        try (FileInputStream inputStream = new FileInputStream(configurationFile)) {
            return DefaultServerConfiguration.load(inputStream);
        } catch (FileNotFoundException e) {
            LOGGER.info("Configuration file server.xml does not exists. Using default configuration.");
        } catch (IOException e) {
            LOGGER.error("An exception occurred while loading configuration file. Using default configuration.", e);
        }
        return new DefaultServerConfiguration();
    }
}
