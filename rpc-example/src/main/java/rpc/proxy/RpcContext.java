package rpc.proxy;

import java.util.HashMap;
import java.util.Map;

public class RpcContext {

    private static ThreadLocal<RpcContext> local = ThreadLocal.withInitial(RpcContext::new);
    private final Map<String, Object> attachments = new HashMap<>();

    public static RpcContext getContext() {
        return local.get();
    }

    public Map<String, Object> getObjectAttachments() {
        return this.attachments;
    }
}
