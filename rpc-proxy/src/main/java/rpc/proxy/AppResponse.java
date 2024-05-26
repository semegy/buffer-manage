package rpc.proxy;//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import java.io.Serializable;

public class AppResponse implements Result, Serializable {
    private static final long serialVersionUID = -6925924956850004727L;
    private Object result;

    private long requestId;

    public AppResponse(long requestId) {
        this.requestId = requestId;
    }


    public AppResponse() {
    }


    @Override
    public Object getValue() {
        return result;
    }

    @Override
    public void setValue(Object value) {
        this.result = value;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }
}
