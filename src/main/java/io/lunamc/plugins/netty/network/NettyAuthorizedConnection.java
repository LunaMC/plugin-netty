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
import io.lunamc.common.login.session.Profile;
import io.lunamc.common.network.AuthorizedConnection;
import io.netty.channel.Channel;

import java.util.Objects;

public class NettyAuthorizedConnection extends NettyDecidedConnection implements AuthorizedConnection {

    private final Profile profile;
    private final VirtualHost.Compression compression;

    public NettyAuthorizedConnection(Channel channel,
                                     int protocolVersion,
                                     String serverAddress,
                                     int port,
                                     VirtualHost virtualHost,
                                     Profile profile,
                                     VirtualHost.Compression compression) {
        super(channel, protocolVersion, serverAddress, port, virtualHost);

        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.compression = compression;
    }

    @Override
    public Profile getProfile() {
        return profile;
    }

    @Override
    public VirtualHost.Compression getCompression() {
        return compression;
    }
}
