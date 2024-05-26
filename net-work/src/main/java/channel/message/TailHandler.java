package channel.message;

import buffer.ByteBuf;
import channel.ChannelContainer;
import channel.ChannelContext;
import channel.ChannelWrapper;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class TailHandler implements EventHandler<ByteBuf> {
    @Override
    public void connect(SocketChannel channel) throws IOException {

    }

    @Override
    public void read(ChannelContext context, ByteBuf byteBuf) {
        byteBuf.deallocate();
    }

    @Override
    public void accept(ChannelContext context) throws IOException {

    }

    @Override
    public void exception(ChannelWrapper channelWrapper, Throwable cause) {

    }

    @Override
    public void userEventTriggered(ChannelWrapper channelWrapper, Object evt) {

    }

    @Override
    public void handleAdd(EventHandler handler) {

    }

    @Override
    public void handleRemove(ChannelContainer context) {

    }

    @Override
    public EventHandler nextHandler() {
        return null;
    }

    @Override
    public void exception(ChannelContext context, Throwable e) {

    }

    @Override
    public void write(ChannelContext ctx, ByteBuf out) {
        SocketChannel channel = (SocketChannel) ctx.channel();
        out.writeAndFlush(channel);
        out.deallocate();
    }
}
