package rpc.proxy;

import common.ReflectUtils;
import javassist.*;

import java.lang.reflect.Method;

import static common.ArgumentUtils.asArgument;

public abstract class Proxy {
    public static Proxy getProxy(Class<?> ifs) {
        ClassPool pool = ClassPool.getDefault();
        CtClass proxyClass = pool.makeClass(ifs.getName() + "Proxy");
        Package pkg = ifs.getPackage();
        pool.importPackage(pkg.getName());
        try {
            proxyClass.setSuperclass(pool.get(Proxy.class.getName()));
            proxyClass.addInterface(pool.get(ifs.getName()));
            proxyClass.addField(new CtField(pool.get(Method[].class.getName()), "methods", proxyClass));
            proxyClass.addField(new CtField(pool.get(InvocationHandler.class.getName()), "handler", proxyClass));
            proxyClass.addMethod(CtMethod.make("public Object initHandler(rpc.proxy.InvocationHandler handler) { this.handler = handler; return this; }", proxyClass));
            proxyClass.addMethod(CtMethod.make("public void addMethods(java.lang.Class clazz) { this.methods = clazz.getDeclaredMethods(); }", proxyClass));
            Method[] ms = ifs.getDeclaredMethods();
            for (int i = 0; i < ms.length; i++) {
                Method method = ms[i];
                Class<?> rt = method.getReturnType();
                StringBuilder sb = new StringBuilder();
                sb.append("public ")
                        .append(method.getReturnType().getName()).append(" ")
                        .append(method.getName());
                Class<?>[] pts = method.getParameterTypes();
                sb.append("(");
                if (pts.length == 0) {
                    sb.append(")");
                } else if (pts.length == 1) {
                    Class<?> param = pts[0];
                    sb.append(param.getSimpleName() + " arg0)");
                } else {
                    for (int j = 0; j < pts.length - 1; j++) {
                        Class<?> param = pts[j];
                        sb.append(param.getSimpleName() + " arg" + j + ",");
                    }
                    Class<?> param = pts[pts.length - 1];
                    sb.append(param.getSimpleName() + " arg")
                            .append(pts.length - 1)
                            .append(")");
                }

                Class<?>[] ets = method.getExceptionTypes();
                if (ets.length > 0) {
                    sb.append(" throws ");
                    if (ets.length > 1) {
                        for (int i1 = ets.length - 1; i1 > 0; i1--) {
                            sb.append(ets[i1].getName())
                                    .append(",");
                        }
                    }
                    sb.append(ets[0].getName());
                }
                sb.append("{");
                if (pts.length == 0) {
                    sb.append("Object[] args = new Object[0];");
                } else if (pts.length >= 1) {
                    sb.append("Object[] args = new Object[" + pts.length + "];");
                    for (int j = 0; j < pts.length; j++) {
                        sb.append("args[").append(j).append("] = arg").append(j).append(";");
                    }
                }
                if (!rt.equals(Void.TYPE)) {
                    sb.append("Object ret = handler.invoke(this, methods[").append(i).append("], args,").append(i).append(" );");
                    sb.append("return " + asArgument(method.getReturnType(), "ret ;}"));
                }
                sb.append("}");
                CtMethod make = CtMethod.make(sb.toString(), proxyClass);
                proxyClass.addMethod(make);
            }
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
        return ReflectUtils.instance(proxyClass);
    }

    public abstract <T> T initHandler(InvocationHandler handler);

    public abstract void addMethods(Class<?> clazz);
}
