package com.rpc.proxy;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rpc.proxy.AppResponse;
import rpc.proxy.Invocation;
import rpc.proxy.Request;
import rpc.proxy.Result;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class AsyncRpcResult implements Result {
    private static final Logger logger = LoggerFactory.getLogger(AsyncRpcResult.class);

    private static final AtomicLong INVOKE_ID = new AtomicLong(0L);
    private final Request request;
    private RpcContext storedContext;
    private RpcContext storedServerContext;
    private Executor executor;
    private Invocation invocation;
    private CompletableFuture<AppResponse> responseFuture;
    private RpcContext tmpContext;
    private RpcContext tmpServerContext;

    private Long requestId;
    private BiConsumer<Result, Throwable> beforeContext = (appResponse, t) -> {
        this.tmpContext = RpcContext.getContext();
//        this.tmpServerContext = RpcContext.getServerContext();
//        RpcContext.restoreContext(this.storedContext);
//        RpcContext.restoreServerContext(this.storedServerContext);
    };
    private BiConsumer<Result, Throwable> afterContext = (appResponse, t) -> {
//        RpcContext.restoreContext(this.tmpContext);
//        RpcContext.restoreServerContext(this.tmpServerContext);
    };

    public AsyncRpcResult(Invocation invocation) {
        this.invocation = invocation;
        this.storedContext = RpcContext.getContext();
        this.requestId = INVOKE_ID.getAndIncrement();
        request = new Request(invocation.getServiceName(), this.requestId, invocation.getParam(), invocation.getMethodName(), invocation.getMethodIndex());
        this.storedServerContext = RpcContext.getContext();
    }

    public Object getValue() {
        return this.getAppResponse().getValue();
    }

    public void setValue(Object value) {
        try {
            if (this.responseFuture.isDone()) {
                ((AppResponse) this.responseFuture.get()).setValue(value);
            } else {
                AppResponse appResponse = new AppResponse();
                appResponse.setValue(value);
                this.responseFuture.complete(appResponse);
            }

        } catch (Exception var3) {
            logger.error("Got exception when trying to fetch the underlying result from AsyncRpcResult.");
//            throw new RpcException(var3);
        }
    }


    public CompletableFuture<AppResponse> getResponseFuture() {
        return this.responseFuture;
    }

    public void setResponseFuture(CompletableFuture<AppResponse> responseFuture) {
        this.responseFuture = responseFuture;
    }

    public Result getAppResponse() {
        try {
            if (this.responseFuture.isDone()) {
                return (Result) this.responseFuture.get();
            }
        } catch (Exception var2) {
            logger.error("Got exception when trying to fetch the underlying result from AsyncRpcResult.");
//            throw new RpcException(var2);
        }

        return createDefaultValue(this.invocation);
    }

    public Result get() throws InterruptedException, ExecutionException {
//        if (this.executor != null && this.executor instanceof ThreadlessExecutor) {
//            ThreadlessExecutor threadlessExecutor = (ThreadlessExecutor)this.executor;
//            threadlessExecutor.waitAndDrain();
//        }

        return (Result) this.responseFuture.get();
    }

    public Result get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return (Result) this.responseFuture.get(timeout, unit);
    }

    public Result whenCompleteWithContext(BiConsumer<Result, Throwable> fn) {
        this.responseFuture = this.responseFuture.whenComplete((v, t) -> {
            this.beforeContext.accept(v, t);
            fn.accept(v, t);
            this.afterContext.accept(v, t);
        });
        return this;
    }

    public <U> CompletableFuture<U> thenApply(Function<Result, ? extends U> fn) {
        return this.responseFuture.thenApply(fn);
    }

    /**
     * @deprecated
     */
    @Deprecated
//    public Map<String, String> getAttachments() {
//        return this.getAppResponse().getAttachments();
//    }

//    public Map<String, Object> getObjectAttachments() {
//        return this.getAppResponse().getObjectAttachments();
//    }

//    public void setAttachments(Map<String, String> map) {
//        this.getAppResponse().setAttachments(map);
//    }

//    public void setObjectAttachments(Map<String, Object> map) {
//        this.getAppResponse().setObjectAttachments(map);
//    }

    /** @deprecated */
//    @Deprecated
//    public void addAttachments(Map<String, String> map) {
//        this.getAppResponse().addAttachments(map);
//    }

//    public void addObjectAttachments(Map<String, Object> map) {
//        this.getAppResponse().addObjectAttachments(map);
//    }
//
//    public String getAttachment(String key) {
//        return this.getAppResponse().getAttachment(key);
//    }
//
//    public Object getObjectAttachment(String key) {
//        return this.getAppResponse().getObjectAttachment(key);
//    }
//
//    public String getAttachment(String key, String defaultValue) {
//        return this.getAppResponse().getAttachment(key, defaultValue);
//    }
//
//    public Object getObjectAttachment(String key, Object defaultValue) {
//        return this.getAppResponse().getObjectAttachment(key, defaultValue);
//    }
//
//    public void setAttachment(String key, String value) {
//        this.setObjectAttachment(key, value);
//    }
//
//    public void setAttachment(String key, Object value) {
//        this.setObjectAttachment(key, value);
//    }
//
//    public void setObjectAttachment(String key, Object value) {
//        this.getAppResponse().setAttachment(key, value);
//    }
//
    public Executor getExecutor() {
        return this.executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    private static Result createDefaultValue(Invocation invocation) {
//        ConsumerMethodModel method = (ConsumerMethodModel)invocation.get("methodModel");
        return new AppResponse();
    }

    public Request getRequest() {
        return request;
    }

    public Long getRequestId() {
        return requestId;
    }
}
