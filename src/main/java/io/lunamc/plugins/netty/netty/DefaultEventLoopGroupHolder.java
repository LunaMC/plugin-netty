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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DefaultEventLoopGroupHolder implements EventLoopGroupHolder {

    private EventLoopGroup bossGroup;
    private EventLoopGroup bossGroupView;
    private EventLoopGroup workerGroup;
    private EventLoopGroup workerGroupView;

    @Override
    public void start() {
        bossGroup = NettyUtils.createEventLoopGroup(1);
        bossGroupView = new IndestructibleEventLoopGroup(bossGroup);
        workerGroup = NettyUtils.createEventLoopGroup();
        workerGroupView = new IndestructibleEventLoopGroup(workerGroup);
    }

    @Override
    public synchronized void shutdown() {
        Future<?> bossShutdown = null;
        if (bossGroup != null && shouldShutDown(bossGroup)) {
            bossShutdown = bossGroup.shutdownGracefully();
            bossGroup = null;
            bossGroupView = null;
        }

        Future<?> workerShutdown = null;
        if (workerGroup != null) {
            workerShutdown = workerGroup.shutdownGracefully();
            workerGroup = null;
            workerGroupView = null;
        }

        if (bossShutdown != null)
            safeGet(bossShutdown);
        if (workerShutdown != null)
            safeGet(workerShutdown);
    }

    @Override
    public EventLoopGroup getBossGroup() {
        return bossGroupView;
    }

    @Override
    public EventLoopGroup getWorkerGroup() {
        return workerGroupView;
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

    @SuppressWarnings("deprecation")
    private static class IndestructibleEventLoopGroup implements EventLoopGroup {

        private final EventLoopGroup delegate;

        private IndestructibleEventLoopGroup(EventLoopGroup delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public EventLoop next() {
            return delegate.next();
        }

        @Override
        public ChannelFuture register(Channel channel) {
            return delegate.register(channel);
        }

        @Override
        public ChannelFuture register(ChannelPromise channelPromise) {
            return delegate.register(channelPromise);
        }

        @Override
        public ChannelFuture register(Channel channel, ChannelPromise channelPromise) {
            return delegate.register(channel, channelPromise);
        }

        @Override
        public boolean isShuttingDown() {
            return delegate.isShuttingDown();
        }

        @Override
        public io.netty.util.concurrent.Future<?> shutdownGracefully() {
            throw new UnsupportedOperationException("This event loop group cannot be shut down");
        }

        @Override
        public io.netty.util.concurrent.Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("This event loop group cannot be shut down");
        }

        @Override
        public io.netty.util.concurrent.Future<?> terminationFuture() {
            return delegate.terminationFuture();
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException("This event loop group cannot be shut down");
        }

        @Override
        public List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException("This event loop group cannot be shut down");
        }

        @Override
        public Iterator<EventExecutor> iterator() {
            return delegate.iterator();
        }

        @Override
        public io.netty.util.concurrent.Future<?> submit(Runnable task) {
            return delegate.submit(task);
        }

        @Override
        public <T> io.netty.util.concurrent.Future<T> submit(Runnable task, T result) {
            return delegate.submit(task, result);
        }

        @Override
        public <T> io.netty.util.concurrent.Future<T> submit(Callable<T> task) {
            return delegate.submit(task);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return delegate.schedule(command, delay, unit);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return delegate.schedule(callable, delay, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return delegate.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return delegate.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return delegate.invokeAll(tasks);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.invokeAll(tasks, timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return delegate.invokeAny(tasks);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.invokeAny(tasks, timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
        }
    }
}
