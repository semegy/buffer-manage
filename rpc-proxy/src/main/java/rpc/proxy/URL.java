package rpc.proxy;

import java.util.Map;

public class URL {

    Map<String, String> parameter;
    private String serviceKey;
    private String address;
    private String host;

    public String getParameter(String param) {
        return parameter.get(param);
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getHost() {
        return this.host;
    }
}
