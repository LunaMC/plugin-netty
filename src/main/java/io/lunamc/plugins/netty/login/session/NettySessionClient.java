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

package io.lunamc.plugins.netty.login.session;

import io.lunamc.platform.service.ServiceRegistration;
import io.lunamc.platform.service.Shutdownable;
import io.lunamc.common.json.JsonMapper;
import io.lunamc.common.login.session.Profile;
import io.lunamc.common.login.session.SessionClient;
import io.lunamc.plugins.netty.netty.EventLoopGroupHolder;
import io.lunamc.plugins.netty.utils.HexUtils;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Response;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class NettySessionClient implements SessionClient, Shutdownable {

    private static final String URL = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=%s&serverId=%s";
    private static final String HASHING_ALGORITHM = "SHA1";

    private final ServiceRegistration<EventLoopGroupHolder> eventLoopGroupHolder;
    private final ServiceRegistration<JsonMapper> jsonMapper;
    private DefaultAsyncHttpClient client;

    public NettySessionClient(ServiceRegistration<EventLoopGroupHolder> eventLoopGroupHolder,
                              ServiceRegistration<JsonMapper> jsonMapper) {
        this.eventLoopGroupHolder = Objects.requireNonNull(eventLoopGroupHolder, "eventLoopGroupHolder must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    @Override
    public CompletableFuture<Profile> annotateJoin(String username, String serverId, byte[] sharedSecret, PublicKey publicKey) {
        MessageDigest md = createMessageDigest();
        md.update(serverId.getBytes(CharsetUtil.ISO_8859_1));
        md.update(sharedSecret);
        md.update(publicKey.getEncoded());
        String sha = HexUtils.toHexTwosComplement(md.digest());

        String url = String.format(URL, encode(username), encode(sha));
        CompletableFuture<Response> response = getClient().prepareGet(url).execute().toCompletableFuture();
        return response.thenApply(r -> {
            if (r == null || !r.hasResponseBody())
                throw new UnsupportedOperationException("No response");
            int status = r.getStatusCode();
            if (status < 200 || status > 299 || status == HttpResponseStatus.NO_CONTENT.code())
                throw new UnsupportedOperationException("Unexpected status code: " + status);
            String responseBody = r.getResponseBody();
            if (responseBody == null || responseBody.length() < 1)
                throw new UnsupportedOperationException("No response body");
            return jsonMapper.requireInstance().deserialize(Profile.class, r.getResponseBody());
        });
    }

    @Override
    public void shutdown() {
        if (client != null)
            client.close();
    }

    private AsyncHttpClient getClient() {
        if (client == null || client.isClosed()) {
            // Creates client initially
            client = createClient();
        } else {
            // Previous event loop group was terminated but a new instance may be available in the EventLoopGroupHolder
            EventLoopGroup eventLoopGroup = client.getEventLoopGroup();
            if (!isAvailable(eventLoopGroup))
                client = createClient();
            if (!isAvailable(client.getEventLoopGroup()))
                throw new IllegalStateException("Event loop group is (still) not available");
        }
        return client;
    }

    private DefaultAsyncHttpClient createClient() {
        EventLoopGroupHolder eventLoopGroupHolder = this.eventLoopGroupHolder.requireInstance();
        return new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setEventLoopGroup(eventLoopGroupHolder.getWorkerGroup())
                .setUseNativeTransport(false)
                .build());
    }

    private static boolean isAvailable(EventLoopGroup eventLoopGroup) {
        return !eventLoopGroup.isShuttingDown() && !eventLoopGroup.isShutdown() && !eventLoopGroup.isTerminated();
    }

    private static String encode(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance(HASHING_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
