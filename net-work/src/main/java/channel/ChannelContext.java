package channel;

import buffer.ByteBuf;
import channel.message.EventHandler;
import channel.message.HeadHandler;
import channel.nio.NioReactorEndpoint;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static channel.ChannelContainer.bufferAllocate;

public class ChannelContext<T extends SelectableChannel> {

    ChannelContainer container;
    T channel;
    NioReactorEndpoint execute;
    Selector select;
    public final EventHandler HEAD_HANDLER = new HeadHandler();

    Object attachment;
    volatile private int state;

    AtomicIntegerFieldUpdater stateUpdater = AtomicIntegerFieldUpdater.newUpdater(ChannelContext.class, "state");

    public ChannelContext(ChannelContainer container, T channel) {
        this.channel = channel;
        this.execute = container.nextEndpoint();
        select = execute.selector();
        container.handlerInitializer.initHandler(this);
    }

    public ChannelContext(ChannelContainer<T> container) {
        this.container = container;
        this.channel = container.channel;
        this.execute = container.boos;
        select = execute.selector();
    }

    public void invokerAccept() {
        try {
            SocketChannel socketChannel = ((ServerSocketChannel) channel).accept();
            socketChannel.configureBlocking(false);
            ChannelContext channelContext = new ChannelContext(container, socketChannel);
            channelContext.register(SelectionKey.OP_READ);
            channelContext.loop();
            HEAD_HANDLER.accept(channelContext);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void loop() {
        if (this.state == 0 && stateUpdater.compareAndSet(this, 0, 1)) {
            this.execute.start();
        }
    }

    public void register(int ops) {
        execute.register(channel, ops, this);
    }

    public void invokerRead() {
        ByteBuf byteBuf = bufferAllocate.allocate();
        HEAD_HANDLER.read(this, byteBuf);
    }

    public void invokerWrite() {
        try {
            HEAD_HANDLER.write(this, attachment);
        } catch (Exception e) {
            HEAD_HANDLER.exception(this, e);
        }
    }

    public void write(Object obj) {
        try {
            HEAD_HANDLER.write(this, obj);
        } catch (Exception e) {
            e.printStackTrace();
            HEAD_HANDLER.exception(this, e);
        }
    }

    public ChannelContext<T> addHandler(EventHandler handler) {
        this.HEAD_HANDLER.handleAdd(handler);
        return this;
    }

    public T channel() {
        return channel;
    }

    public ChannelContext<T> connect(InetSocketAddress address) {
        try {
            SocketChannel ch = (SocketChannel) this.channel;
            boolean connect = ch.connect(address);
            do {
                ch.finishConnect();
                connect = ch.isConnected();
            } while (!connect);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //注册事件
        execute.selector();
        execute.start();
        this.register(SelectionKey.OP_READ);
        return this;
    }

}
