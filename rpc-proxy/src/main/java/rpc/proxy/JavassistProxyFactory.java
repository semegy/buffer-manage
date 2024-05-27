package rpc.proxy;


import common.ReflectUtils;

import java.util.HashSet;
import java.util.Set;

public class JavassistProxyFactory implements ProxyFactory {

    @Override
    public <T> T getProxy(Invoker<T> invoker) {
        Set<Class<?>> interfaces = new HashSet();

        URL url = invoker.getUrl();

        String interfacesName = url.getParameter("interfaces");

        if (interfacesName != null) {
            String[] types = interfacesName.split(",");
            for (int i = 0; i < types.length; i++) {
                interfaces.add(ReflectUtils.classForName(types[i]));
            }
        }

        interfaces.add(invoker.getInterface());

        return this.getProxy(invoker, invoker.getInterface());
    }

    @Override
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return null;
    }

    public <T> T getProxy(Invoker<T> invoker, Class<?> ifs) {
        return Proxy.getProxy(ifs).initHandler(new InvokerInvocationHandler(invoker));
    }


}
