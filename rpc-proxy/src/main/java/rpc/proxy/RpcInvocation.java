package rpc.proxy;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;

public class RpcInvocation extends Invocation implements Serializable {

    private static final long serialVersionUID = -4355285085441097045L;


    public RpcInvocation(Method method, String serviceName, Object[] arguments, int methodIndex) {
        super(method, serviceName, arguments, methodIndex);
    }

    public RpcInvocation(Method method, String serviceName, Object[] arguments, Map<String, Object> attachment, Map<Object, Object> attributes, int methodIndex) {
        super(method, serviceName, arguments, attachment, attributes, methodIndex);
    }

    public RpcInvocation(String methodName, String serviceName, Class<?>[] parameterTypes, Object[] arguments, Map<String, Object> attachments, Invoker<?> invoker, Map<Object, Object> attributes, int methodIndex) {
        super(methodName, serviceName, parameterTypes, arguments, attachments, invoker, attributes, methodIndex);
    }
}
