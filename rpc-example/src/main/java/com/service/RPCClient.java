package com.service;

import channel.ChannelContainer;
import channel.ChannelContext;
import channel.message.HandlerInitializer;
import rpc.proxy.RpcDecoder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class RPCClient {
    public static final HandlerInitializer initializer =
            context -> context.addHandler(new RpcDecoder(Integer.MAX_VALUE, 0, 2, 0, 2))
                    .addHandler(new RpcEncoder(Integer.MAX_VALUE, 0, 2, 0, 2));

    public final ChannelContainer channelContainer;

    ChannelContext[] clientChannels;

    static int GROUP_SIZE = 16;
    static int NEXT_ENDPOINT_INDEX_MAX = GROUP_SIZE << 8;

    {
        clientChannels = new ChannelContext[GROUP_SIZE];
        channelContainer = new ChannelContainer(initializer, GROUP_SIZE, null);
        InetSocketAddress address = new InetSocketAddress("localhost", 80);
        for (int i = 0; i < clientChannels.length; i++) {
            try {
                SocketChannel channel = SocketChannel.open();
                channel.configureBlocking(false);
                ChannelContext channelContext = new ChannelContext(channelContainer, channel);
                clientChannels[i] = channelContext.connect(address);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    final AtomicInteger nextClientIndex = new AtomicInteger(0);

    public ChannelContext nextChannel() {
        ChannelContext clientChannel = clientChannels[nextClientIndex.getAndIncrement() & GROUP_SIZE - 1];
        swapIndex();
        return clientChannel;
    }

    private void swapIndex() {
        if (nextClientIndex.get() < NEXT_ENDPOINT_INDEX_MAX) {
            nextClientIndex.compareAndSet(NEXT_ENDPOINT_INDEX_MAX, 0);
        }
    }

}
