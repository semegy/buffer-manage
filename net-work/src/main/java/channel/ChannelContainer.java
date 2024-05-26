package channel;

import buffer.BufferAllocate;
import buffer.pool.PooledBufferAllocate;
import channel.message.HandlerInitializer;
import channel.nio.NioReactorEndpoint;

import java.io.IOException;
import java.net.InetSocketAddress;
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

    public ChannelContext connect(InetSocketAddress address) {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            ChannelContext context = new ChannelContext(this, socketChannel);
            context.connect(address);
            return context;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    void initChannel(InetSocketAddress address) {
        boos = new NioReactorEndpoint();
        try {
            this.channel = (T) ServerSocketChannel.open().bind(address);
            channel.configureBlocking(false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ChannelContainer(HandlerInitializer initializer, int groupSize, InetSocketAddress address) {
        this(initializer, groupSize);
        boos = new NioReactorEndpoint();
        initChannel(address);
    }

    public ChannelContainer(HandlerInitializer initializer, int groupSize) {
        this.handlerInitializer = initializer;
        groupSize = groupSize <= 0 ? 1 : groupSize;
        NEXT_ENDPOINT_INDEX_MAX = groupSize << 8;
        chooser = (groupSize & -groupSize) == groupSize ? new PowerOfTwoEndpointChooser(groupSize) : new EndpointChooser(groupSize);
        workerGroup = new NioReactorEndpoint[groupSize];
        for (int i = 0; i < groupSize; i++) {
            workerGroup[i] = new NioReactorEndpoint();
        }
        bufferAllocate = new PooledBufferAllocate(groupSize, PAGE_SIZE, DEFAULT_MAX_ORDER, DEFAULT_SMALL_CACHE_SIZE, DEFAULT_NORMAL_CACHE_SIZE, true);
    }

}
