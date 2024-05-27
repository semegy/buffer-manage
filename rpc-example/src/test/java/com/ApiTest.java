package com;

import com.provider.service.Api;
import com.rpc.proxy.AbstractInvoker;
import common.ReflectUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import rpc.proxy.InvokerInvocationHandler;
import rpc.proxy.Proxy;

@SpringBootTest
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Import({ServiceScansRegister.class})
@ServiceScans(basePackages = {"com.provider.service"})
public class ApiTest {

    @Test
    void consumerApi() {

        // 通过Proxy.getProxy()创建SampleInterface的代理实例
        Api proxyInstance = (Api) Proxy.getProxy(Api.class);
        ((Proxy) proxyInstance).addMethods(Api.class);
        AbstractInvoker invoker = new AbstractInvoker();
        Class<?> interfaceClass = ReflectUtils.classForName("com.provider.service.Api");
        invoker.interfaceClass = interfaceClass;
        // 设置代理实例的InvocationHandler
        ((Proxy) proxyInstance).initHandler(new InvokerInvocationHandler(invoker));

        // 测试代理实例的行为是否符合预期
        Assertions.assertEquals("World", proxyInstance.test("World"));

    }
}
