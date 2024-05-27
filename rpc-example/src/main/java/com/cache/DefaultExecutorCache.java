package com.cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultExecutorCache implements ExecutorCache {

    // 守护线程池
    private final ExecutorService SHARED_EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        protected final AtomicInteger mThreadNum = new AtomicInteger(1);
        protected final String mPrefix = "share-thread-";
        SecurityManager s = System.getSecurityManager();
        protected final ThreadGroup mGroup = s == null ? Thread.currentThread().getThreadGroup() : s.getThreadGroup();

        // 守护线程
        @Override
        public Thread newThread(Runnable runnable) {
            String name = this.mPrefix + this.mThreadNum.getAndIncrement();
            Thread ret = new Thread(this.mGroup, runnable, name, 0L);
            ret.setDaemon(true);
            return ret;
        }
    });

    private final ExecutorService FIX_EXECUTOR = Executors.newFixedThreadPool(200);

    @Override
    public ExecutorService getSharedExecutor() {
        return this.SHARED_EXECUTOR;
    }

    @Override
    public ExecutorService getExecutor() {
        return createFixExecutor();
    }

    private ExecutorService createFixExecutor() {
        return FIX_EXECUTOR;
    }
}
