package src.main.java.common;

import javassist.CannotCompileException;
import javassist.CtClass;

public class ReflectUtils {

    public static Class<?> classForName(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T instance(String className) {
        try {
            return (T) Class.forName(className).newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static ClassLoader classLoader(Class<?> clazz) {
        return clazz.getClassLoader();
    }

    public static <T> T instance(CtClass clazz) {
        try {
            Class<?> aClass = clazz.toClass();
            return (T) aClass.newInstance();
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
