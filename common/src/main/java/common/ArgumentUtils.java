package common;

public class ArgumentUtils {

    public static String asArgument(Class<?> cl, String name) {
        if (cl.isPrimitive()) {
            if (Boolean.TYPE == cl) {
                return name + "==null?false:((Boolean)" + name + ").booleanValue()";
            } else if (Byte.TYPE == cl) {
                return name + "==null?(byte)0:((Byte)" + name + ").byteValue()";
            } else if (Character.TYPE == cl) {
                return name + "==null?(char)0:((Character)" + name + ").charValue()";
            } else if (Double.TYPE == cl) {
                return name + "==null?(double)0:((Double)" + name + ").doubleValue()";
            } else if (Float.TYPE == cl) {
                return name + "==null?(float)0:((Float)" + name + ").floatValue()";
            } else if (Integer.TYPE == cl) {
                return name + "==null?(int)0:((Integer)" + name + ").intValue()";
            } else if (Long.TYPE == cl) {
                return name + "==null?(long)0:((Long)" + name + ").longValue()";
            } else if (Short.TYPE == cl) {
                return name + "==null?(short)0:((Short)" + name + ").shortValue()";
            } else {
                throw new RuntimeException(name + " is unknown primitive type.");
            }
        } else {
            return "(" + getName(cl) + ")" + name;
        }
    }

    public static String getName(Class<?> c) {
        if (!c.isArray()) {
            return c.getName();
        } else {
            StringBuilder sb = new StringBuilder();

            do {
                sb.append("[]");
                c = c.getComponentType();
            } while (c.isArray());

            return c.getName() + sb.toString();
        }
    }


}
