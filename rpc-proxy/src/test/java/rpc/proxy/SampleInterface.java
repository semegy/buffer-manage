package rpc.proxy;

import org.junit.Test;

import java.lang.reflect.Method;

interface SampleInterface {
    String sayHello(String name);

    void doSomething();
}

class ProxyTest {

    @Test
    void testGetProxy() {
        // 通过Proxy.getProxy()创建SampleInterface的代理实例
        SampleInterface proxyInstance = (SampleInterface) Proxy.getProxy(SampleInterface.class);

        // 实现InvocationHandler用于模拟实际的行为
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args, int methodIndex) {
                if ("sayHello".equals(method.getName())) {
                    return "Hello, " + args[0];
                } else if ("doSomething".equals(method.getName())) {
                    // 执行doSomething方法的模拟行为，这里没有返回值
                    return null;
                }
                return null;
            }
        };
        ((Proxy) proxyInstance).addMethods(SampleInterface.class);
        // 设置代理实例的InvocationHandler
        ((Proxy) proxyInstance).initHandler(handler);

        // 测试代理实例的行为是否符合预期
        assert "Hello, World".equals(proxyInstance.sayHello("World"));
        proxyInstance.doSomething();  // 这里没有返回值，但是我们假设方法被成功调用
    }
}
