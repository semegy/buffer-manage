package com.cache;

public interface Caches<T> {

    public T getValue();

    public T newInstance();
}
