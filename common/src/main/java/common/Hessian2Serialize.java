package src.main.java.common;

import com.caucho.hessian.io.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author
 * @note Hessian工具类.
 */
public class Hessian2Serialize {

    /**
     * JavaBean序列化.
     *
     * @param javaBean Java对象.
     * @throws Exception 异常信息.
     */
    public static <T> byte[] serialize(T javaBean) {

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        AbstractHessianOutput out = new Hessian2Output(os);

        out.setSerializerFactory(new SerializerFactory());
        try {
            out.writeObject(javaBean);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                out.close();
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return os.toByteArray();
    }

    /**
     * JavaBean反序列化.
     *
     * @throws Exception 异常信息.
     */
    public static <T> T deserialize(byte[] serializeData) {
        ByteArrayInputStream is = new ByteArrayInputStream(serializeData);
        AbstractHessianInput in = new Hessian2Input(is);
        in.setSerializerFactory(new SerializerFactory());
        T value;
        try {
            value = (T) in.readObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                in.close();
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return value;
    }

}
