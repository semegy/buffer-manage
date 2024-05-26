package rpc.proxy;

import java.io.Serializable;

public interface Result extends Serializable {

    Object getValue();

    void setValue(Object value);

}
