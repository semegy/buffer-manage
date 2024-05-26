package com;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ServiceBean<T> {
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
}
