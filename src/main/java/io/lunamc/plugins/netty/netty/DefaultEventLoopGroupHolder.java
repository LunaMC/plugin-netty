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

package io.lunamc.plugins.netty.netty;

import io.lunamc.plugins.netty.utils.NettyUtils;
import io.netty.channel.EventLoopGroup;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DefaultEventLoopGroupHolder implements EventLoopGroupHolder {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @Override
    public void start() {
        bossGroup = NettyUtils.createEventLoopGroup(1);
        workerGroup = NettyUtils.createEventLoopGroup();
    }

    @Override
    public synchronized void shutdown() {
        Future<?> bossShutdown = null;
        if (bossGroup != null && shouldShutDown(bossGroup)) {
            bossShutdown = bossGroup.shutdownGracefully();
            bossGroup = null;
        }

        Future<?> workerShutdown = null;
        if (workerGroup != null) {
            workerShutdown = workerGroup.shutdownGracefully();
            workerGroup = null;
        }

        if (bossShutdown != null)
            safeGet(bossShutdown);
        if (workerShutdown != null)
            safeGet(workerShutdown);
    }

    @Override
    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    @Override
    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    private static boolean shouldShutDown(EventLoopGroup eventLoopGroup) {
        return !eventLoopGroup.isTerminated() && !eventLoopGroup.isShuttingDown() && !eventLoopGroup.isShutdown();
    }

    private static void safeGet(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ignore) {
        }
    }
}
