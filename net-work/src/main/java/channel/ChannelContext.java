package channel;

import buffer.ByteBuf;
import channel.message.EventHandler;
import channel.nio.NioReactorEndpoint;

import java.io.IOException;
import java.nio.channels.*;

import static channel.ChannelContainer.bufferAllocate;

public class ChannelContext<T extends SelectableChannel> {

    ChannelContainer container;
    T channel;
    NioReactorEndpoint execute;
    Selector select;
    EventHandler handler = NioReactorEndpoint.HEAD_HANDLER;

    Object attachment;

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
            handler.accept(channelContext);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void loop() {
        this.execute.start();
    }

    public void register(int ops) {
        execute.register(channel, ops, this);
    }

    public void invokerRead() {
        ByteBuf byteBuf = bufferAllocate.allocate();
        handler.read(this, byteBuf);
    }

    public void invokerWrite() {
        try {
            handler.write(this, attachment);
        } catch (Exception e) {
            handler.exception(this, e);
        }
    }

    public void write(Object obj) {
        try {
            handler.write(this, obj);
        } catch (Exception e) {
            handler.exception(this, e);
        }
    }

    public void invokerConnect(ChannelWrapper wrapper) {
    }

    public ChannelContext<T> addHandler(EventHandler handler) {
        this.handler.handleAdd(handler);
        return this;
    }

    public T channel() {
        return channel;
    }
}
