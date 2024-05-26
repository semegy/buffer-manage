import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class Request implements Externalizable {

    String a;
    int b;
    String c;
    long d;

    public Request(String a, int b, String c, long d, Request e) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
    }

    public Request(String a, int b, String c, long d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }


    Request e;


    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(a);
        out.writeInt(b);
        out.writeUTF(c);
        out.writeLong(d);
        out.writeObject(e);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        a = in.readUTF();
        b = in.readInt();
        c = in.readUTF();
        d = in.readLong();
        e = (Request) in.readObject();
    }
}
