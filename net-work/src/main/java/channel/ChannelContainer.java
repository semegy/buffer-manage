package channel;

import buffer.BufferAllocate;
import buffer.pool.PooledBufferAllocate;
import channel.message.HandlerInitializer;
import channel.nio.NioReactorEndpoint;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import static buffer.pool.PooledBufferAllocate.*;

public class ChannelContainer<T extends SelectableChannel> {
    HandlerInitializer handlerInitializer;

    private static final int PAGE_SIZE = 8192;

    NioReactorEndpoint boos;
    NioReactorEndpoint[] workerGroup;

    EndpointChooser chooser;

    int NEXT_ENDPOINT_INDEX_MAX;

    public static BufferAllocate bufferAllocate;

    public T channel;

    public NioReactorEndpoint nextEndpoint() {
        return chooser.nextEndpoint();
    }

    public void start() {
        ChannelContext channelContext = new ChannelContext(this);
        channelContext.register(SelectionKey.OP_ACCEPT);
        boos.start();
    }

    public void connect(SocketAddress address, HandlerInitializer initializer) {
        SocketChannel socketChannel = (SocketChannel) channel;
        try {
            socketChannel.connect(address);
            while (!socketChannel.finishConnect()) {
                System.out.println("成功");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //注册事件
        ChannelContext channelContext = new ChannelContext(this);
        channelContext.register(SelectionKey.OP_READ);
        initializer.initHandler(channelContext);
        boos.start();
    }

    class EndpointChooser {
        final AtomicInteger nextEndpointIndex = new AtomicInteger(0);

        final int groupSize;

        public EndpointChooser(int groupSize) {
            this.groupSize = groupSize;
        }

        NioReactorEndpoint nextEndpoint() {
            NioReactorEndpoint nioReactorEndpoint = workerGroup[nextEndpointIndex.getAndIncrement() % groupSize];
            swapIndex();
            return nioReactorEndpoint;
        }

        protected void swapIndex() {
            if (nextEndpointIndex.get() < NEXT_ENDPOINT_INDEX_MAX) {
                nextEndpointIndex.compareAndSet(NEXT_ENDPOINT_INDEX_MAX, 0);
            }
        }
    }

    class PowerOfTwoEndpointChooser extends EndpointChooser {

        public PowerOfTwoEndpointChooser(int groupSize) {
            super(groupSize);
        }

        @Override
        NioReactorEndpoint nextEndpoint() {
            NioReactorEndpoint nioReactorEndpoint = workerGroup[nextEndpointIndex.getAndIncrement() & groupSize - 1];
            swapIndex();
            return nioReactorEndpoint;
        }
    }

    void initChannel(Class<T> channelClass, InetSocketAddress address) {
        if (channelClass.equals(ServerSocketChannel.class)) {
            try {
                this.channel = (T) ServerSocketChannel.open().bind(address);
                channel.configureBlocking(false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                this.channel = (T) SocketChannel.open();
                channel.configureBlocking(false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public ChannelContainer(HandlerInitializer initializer, int groupSize, InetSocketAddress address, Class<T> channelClass) {
        this.handlerInitializer = initializer;
        if (groupSize != 0) {
            NEXT_ENDPOINT_INDEX_MAX = groupSize << 8;
            chooser = (groupSize & -groupSize) == groupSize ? new PowerOfTwoEndpointChooser(groupSize) : new EndpointChooser(groupSize);
        }
        bufferAllocate = new PooledBufferAllocate(groupSize, PAGE_SIZE, DEFAULT_MAX_ORDER, DEFAULT_SMALL_CACHE_SIZE, DEFAULT_NORMAL_CACHE_SIZE, true);
        initChannel(channelClass, address);
        boos = new NioReactorEndpoint();
        workerGroup = new NioReactorEndpoint[groupSize];
        for (int i = 0; i < groupSize; i++) {
            workerGroup[i] = new NioReactorEndpoint();
        }
    }

}
