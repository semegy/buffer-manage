package rpc.proxy;

import java.lang.reflect.Method;

public interface InvocationHandler {

    Object invoke(Object proxy, Method method, Object[] args, int methodIndex) throws Throwable;
}
