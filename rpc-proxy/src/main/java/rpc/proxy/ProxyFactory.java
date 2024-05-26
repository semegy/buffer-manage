package rpc.proxy;

public interface ProxyFactory {

    public <T> T getProxy(Invoker<T> invoker);

    <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces);
}
