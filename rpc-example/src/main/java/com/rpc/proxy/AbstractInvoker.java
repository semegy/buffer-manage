package com.rpc.proxy;

import channel.ChannelContext;
import com.cache.DefaultExecutorCache;
import rpc.proxy.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class AbstractInvoker<T> implements Invoker<T> {
    private URL url;
    public Class<T> interfaceClass;

    public ExecutorService executor = new DefaultExecutorCache().getExecutor();

    @Override
    public URL getUrl() {
        return this.url;
    }

    @Override
    public Class<T> getInterface() {
        return this.interfaceClass;
    }

    @Override
    public Result invoke(Invocation invocation) {
        Map<String, Object> contextAttachments = RpcContext.getContext().getObjectAttachments();
        if (contextAttachments != null && contextAttachments.size() != 0) {
            invocation.addObjectAttachments(contextAttachments);
        }
        return this.doInvoke(invocation);
    }

    private Result doInvoke(Invocation invocation) {
        AsyncRpcResult asyncRpcResult = new AsyncRpcResult(invocation);
        CompletableFuture<AppResponse> future = new DefaultFuture<>(asyncRpcResult.getRequestId());
        asyncRpcResult.setResponseFuture(future);
        RPCClient rpcClient = new RPCClient();
        ChannelContext channel = rpcClient.nextChannel();
        future.runAsync(() -> channel.write(asyncRpcResult.getRequest()), executor);
        try {
            AppResponse appResponse = future.get();
            return appResponse;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
