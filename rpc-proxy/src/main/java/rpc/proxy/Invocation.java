package rpc.proxy;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class Invocation implements Serializable {

    private static final long serialVersionUID = -4355285085441097045L;
    // 目标服务唯一标识
    private String targetServiceUniqueName;
    // 调用方法
    private String methodName;
    // 服务名
    private String serviceName;
    // 参数类型
    private transient Class<?>[] parameterTypes;
    private String parameterTypesDesc;
    private String[] compatibleParamSignatures;
    // 参数
    private Object[] arguments;
    // 附加信息
    private Map<String, Object> attachments;
    // 属性
    private Map<Object, Object> attributes;
    // 调度者
    private transient Invoker<?> invoker;
    // 返回类
    private transient Class<?> returnType;
    private transient Type[] returnTypes;
    private int methodIndex;
    ;

    public void addObjectAttachments(Map<String, Object> attachments) {
        if (attachments != null) {
            if (this.attachments == null) {
                this.attachments = new HashMap();
            }
            this.attachments.putAll(attachments);
        }
    }

    public String getMethodName() {
        return this.methodName;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public Object[] getParam() {
        return this.arguments;
    }

    public int getMethodIndex() {
        return this.methodIndex;
    }

    public Invocation(Method method, String serviceName, Object[] arguments, int methodIndex) {
        this((Method) method, serviceName, (Object[]) arguments, (Map) null, (Map) null, methodIndex);
    }

    public Invocation(Method method, String serviceName, Object[] arguments, Map<String, Object> attachment, Map<Object, Object> attributes, int methodIndex) {
        this(method.getName(), serviceName, method.getParameterTypes(), arguments, attachment, null, attributes, methodIndex);
        this.returnType = method.getReturnType();
    }


    public Invocation(String methodName, String serviceName, Class<?>[] parameterTypes, Object[] arguments, Map<String, Object> attachments, Invoker<?> invoker, Map<Object, Object> attributes, int methodIndex) {
        this.methodName = methodName;
        this.serviceName = serviceName;
        this.parameterTypes = parameterTypes == null ? new Class[0] : parameterTypes;
        this.arguments = arguments == null ? new Object[0] : arguments;
        this.attachments = (Map) (attachments == null ? new HashMap() : attachments);
        this.attributes = (Map) (attributes == null ? new HashMap() : attributes);
        this.invoker = invoker;
        this.methodIndex = methodIndex;
    }

    public void setTargetServiceUniqueName(String serviceKey) {
        this.targetServiceUniqueName = targetServiceUniqueName;
    }
}
