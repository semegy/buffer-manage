package com.provider;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceBean<T> implements Serializable {

    public static ConcurrentHashMap<String, ServiceBean> serviceMap = new ConcurrentHashMap<>();
    public T ref;

    public String interfaceName;


    Method[] methods;

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public Method[] getMethods() {
        return methods;
    }

    public void setMethods(Method[] methods) {
        this.methods = methods;
    }


    public ServiceBean() {
    }

    public T getRef() {
        return ref;
    }

    public void setRef(T ref) {
        this.ref = ref;
    }

    public Object invoke(int index, Object[] args) {
        try {
            return methods[index].invoke(ref, args);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @PostConstruct
    public void init() {
        serviceMap.put(this.interfaceName, this);
    }
}
