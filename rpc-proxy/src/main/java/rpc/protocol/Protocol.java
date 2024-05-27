package rpc.protocol;

import rpc.proxy.Invoker;

import java.net.URL;

public interface Protocol {
// 暴露
//    <T> Exporter<T> export(Invoker<T> invoker) throws RpcException;

    /**
     * 拉取服务
     *
     * @param type
     * @param url
     * @param <T>
     * @return
     */
    <T> Invoker<T> refer(Class<T> type, URL url);

    /**
     * 服务销毁
     */
    void destroy();

}
