package com.consummer;

import com.cache.DefaultExecutorCache;
import com.cache.ExecutorCache;
import org.springframework.context.annotation.Bean;
import rpc.proxy.Invoker;

public class ReferenceConfigBean<T> {

    /**
     * bean调度器
     */
    private transient volatile Invoker<?> invoker;

    /**
     * bean
     */
    private transient volatile T ref;

    @Bean
    public ExecutorCache defaultExecutorCache() {
        return new DefaultExecutorCache();
    }

}
