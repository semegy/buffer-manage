package com.service;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface Result extends Serializable {
    Object getValue();

    void setValue(Object value);

    Throwable getException();

    void setException(Throwable t);

    boolean hasException();

    Object recreate() throws Throwable;

//    Map<String, String> getAttachments();

    Map<String, Object> getObjectAttachments();

    void addAttachments(Map<String, String> map);

    void addObjectAttachments(Map<String, Object> map);

    void setAttachments(Map<String, String> map);

    void setObjectAttachments(Map<String, Object> map);

    String getAttachment(String key);

    Object getObjectAttachment(String key);

    String getAttachment(String key, String defaultValue);

    Object getObjectAttachment(String key, Object defaultValue);

    void setAttachment(String key, String value);

    void setAttachment(String key, Object value);

    void setObjectAttachment(String key, Object value);

    Result whenCompleteWithContext(BiConsumer<Result, Throwable> fn);

    <U> CompletableFuture<U> thenApply(Function<Result, ? extends U> fn);

    Result get() throws InterruptedException, ExecutionException;

    Result get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
}

