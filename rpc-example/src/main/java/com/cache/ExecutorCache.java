package com.cache;

import java.util.concurrent.ExecutorService;

public interface ExecutorCache {
    ExecutorService getSharedExecutor();

    public ExecutorService getExecutor();
}
