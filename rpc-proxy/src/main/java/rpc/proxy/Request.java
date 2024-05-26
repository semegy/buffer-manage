package rpc.proxy;

import java.io.Serializable;

public class Request implements Serializable {

    long requestId;

    String interfaceName;

    String method;

    int methodIndex;

    Object[] params;

    public Request(String serviceName, long requestId, Object[] param, String methodName, int methodId) {
        this.requestId = requestId;
        this.interfaceName = serviceName;
        this.params = param;
        this.method = methodName;
        this.methodIndex = methodId;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getMethodIndex() {
        return methodIndex;
    }

    public void setMethodIndex(int methodIndex) {
        this.methodIndex = methodIndex;
    }

    public Object[] getParams() {
        return params;
    }

    public void setParams(Object[] params) {
        this.params = params;
    }
}
