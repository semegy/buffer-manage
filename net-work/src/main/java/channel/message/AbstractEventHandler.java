package channel.message;

import buffer.ByteBuf;
import channel.ChannelContainer;
import channel.ChannelContext;
import channel.ChannelWrapper;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public abstract class AbstractEventHandler<T> implements EventHandler<T> {

    Logger logger = Logger.getLogger(AbstractEventHandler.this.getClass().getName());

    protected EventHandler tail;

    protected EventHandler tailPer;


    EventHandler nextHandler;

    @Override
    public void handleAdd(EventHandler handler) {
        ((AbstractEventHandler) tailPer).nextHandler = handler;
        tailPer = handler;
        ((AbstractEventHandler) tailPer).nextHandler = tail;
    }

    @Override
    public EventHandler nextHandler() {
        return nextHandler;
    }

    @Override
    public void read(ChannelContext context, ByteBuf byteBuf) {
        return;
    }

    @Override
    public void accept(ChannelContext ctx) throws IOException {
        SocketChannel channel = (SocketChannel) ctx.channel();
        SocketAddress remoteAddress = channel.getRemoteAddress();
        logger.info(remoteAddress.toString() + " CONNECT SUCCESS!");
    }


    @Override
    public void write(ChannelContext ctx, T out) {
        if (out == null) {
            return;
        } else {
            nextHandler().write(ctx, out);
        }
    }

    public void write(ChannelContext ctx, ByteBuf out) {
        if (out == null) {
            return;
        } else {
            nextHandler().write(ctx, out);
        }
    }

    @Override
    public void exception(ChannelWrapper channelWrapper, Throwable cause) {

    }

    @Override
    public void userEventTriggered(ChannelWrapper channelWrapper, Object evt) {

    }

    @Override
    public void handleRemove(ChannelContainer context) {

    }

    @Override
    public void exception(ChannelContext context, Throwable e) {

    }

}
