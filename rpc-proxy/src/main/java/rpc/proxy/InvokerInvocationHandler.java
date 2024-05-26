package rpc.proxy;

import java.lang.reflect.Method;

public class InvokerInvocationHandler implements InvocationHandler {

    private final Invoker<?> invoker;

    public InvokerInvocationHandler(Invoker handler) {
        this.invoker = handler;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, int methodIndex) throws Throwable {
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0) {
            if ("toString".equals(methodName)) {
                return this.invoker.toString();
            }

            if ("$destroy".equals(methodName)) {
//                this.invoker.destroy();
                return null;
            }

            if ("hashCode".equals(methodName)) {
                return this.invoker.hashCode();
            }
        } else if (parameterTypes.length == 1 && "equals".equals(methodName)) {
            return this.invoker.equals(args[0]);
        }

        RpcInvocation rpcInvocation = new RpcInvocation(method, this.invoker.getInterface().getName(), args, methodIndex);
        return this.invoker.invoke(rpcInvocation).getValue();
    }
}
