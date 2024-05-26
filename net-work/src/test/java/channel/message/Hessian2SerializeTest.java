package channel.message;

import org.junit.Test;
import src.main.java.common.Hessian2Serialize;

import java.io.Serializable;

public class Hessian2SerializeTest {

    @Test
    public void testSerializeWithPersonObject() throws Exception {
        // 创建一个测试对象
        Person person = new Person("John Doe", 30);
        Person father = new Person("SUNNI Doe", 55);
        person.father = father;
        // 序列化测试对象
        byte[] serializedPerson = Hessian2Serialize.serialize(person);
        assert serializedPerson != null;
        assert serializedPerson.length > 0;
        // 反序列化数据
        Person deserializedBean = Hessian2Serialize.deserialize(serializedPerson);

        // 验证反序列化的对象是否与原始对象属性相等
        assert deserializedBean.name.equals(person.name);
    }

    // 定义一个用于测试的JavaBean
    static class Person implements Serializable {
        private String name;
        private int age;

        public Person father;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

//        @Override
//        public void writeExternal(ObjectOutput out) throws IOException {
//            out.writeUTF(name);
//            out.writeInt(age);
//            out.writeObject(father);
//        }
//
//        @Override
//        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
//            name = in.readUTF();
//            age = in.readInt();
//            father = (Person) in.readObject();
//        }
    }
}
