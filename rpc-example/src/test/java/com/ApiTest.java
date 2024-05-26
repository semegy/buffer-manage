package com;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rpc.proxy.AbstractInvoker;
import rpc.proxy.InvokerInvocationHandler;
import rpc.proxy.Proxy;
import src.main.java.common.ReflectUtils;

public class ApiTest {


    @Test
    void testGetProxy() {
        // 通过Proxy.getProxy()创建SampleInterface的代理实例
        Api proxyInstance = (Api) Proxy.getProxy(Api.class);

        ((Proxy) proxyInstance).addMethods(Api.class);
        AbstractInvoker invoker = new AbstractInvoker();
        Class<?> interfaceClass = ReflectUtils.classForName("com.Api");
        invoker.interfaceClass = interfaceClass;
        // 设置代理实例的InvocationHandler
        ((Proxy) proxyInstance).initHandler(new InvokerInvocationHandler(invoker));

        // 测试代理实例的行为是否符合预期
        Assertions.assertEquals("World", proxyInstance.test("World"));

    }
}
