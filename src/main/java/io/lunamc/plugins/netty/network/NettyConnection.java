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

import io.lunamc.common.network.Connection;
import io.netty.channel.Channel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;

public class NettyConnection implements Connection {

    private final Channel channel;
    private final boolean locallyConnected;

    public NettyConnection(Channel channel) {
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.locallyConnected = isLocalAddress(channel.remoteAddress());
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public boolean isLocallyConnected() {
        return locallyConnected;
    }

    public Channel channel() {
        return channel;
    }

    private boolean isLocalAddress(SocketAddress remote) {
        if (remote instanceof InetSocketAddress) {
            InetAddress inetAddress = ((InetSocketAddress) remote).getAddress();
            return inetAddress.isAnyLocalAddress() ||inetAddress.isLoopbackAddress();
        }
        return false;
    }
}
