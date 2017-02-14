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

package io.lunamc.plugins.netty.network;

import io.lunamc.common.host.VirtualHost;
import io.lunamc.common.network.DecidedConnection;
import io.netty.channel.Channel;

import java.util.Objects;

public class NettyDecidedConnection extends NettyInitializedConnection implements DecidedConnection {

    private final VirtualHost virtualHost;

    public NettyDecidedConnection(Channel channel,
                                  int protocolVersion,
                                  String serverAddress,
                                  int port,
                                  VirtualHost virtualHost) {
        super(channel, protocolVersion, serverAddress, port);

        this.virtualHost = Objects.requireNonNull(virtualHost, "virtualHost must not be null");
    }

    @Override
    public VirtualHost getVirtualHost() {
        return virtualHost;
    }
}
