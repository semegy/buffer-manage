package rpc.proxy;

public interface Invoker<T> {

    URL getUrl();

    Class<T> getInterface();

    Result invoke(Invocation invocation);

}
