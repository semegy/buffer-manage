package com.rpc.proxy;

import channel.ChannelContainer;
import channel.ChannelContext;
import channel.message.HandlerInitializer;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;

@Component
public class RpcService {

    public static boolean done = false;

    HandlerInitializer initializer = new HandlerInitializer() {
        @Override
        public void initHandler(ChannelContext context) {
            context.addHandler(new RpcDecoder(Integer.MAX_VALUE, 0, 2, 0, 2))
                    .addHandler(new RpcEncoder(Integer.MAX_VALUE, 0, 2, 0, 2));
        }
    };

    public RpcService() {
    }

    @PostConstruct
    public void init() {
        InetSocketAddress address = new InetSocketAddress("localhost", 80);
        ChannelContainer context = new ChannelContainer(initializer, 16, address);
        context.start();
    }


}
