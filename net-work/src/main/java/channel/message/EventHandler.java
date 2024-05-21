package channel.message;

import buffer.ByteBuf;
import channel.ChannelContainer;
import channel.ChannelContext;
import channel.ChannelWrapper;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public interface EventHandler<T> {


    void connect(SocketChannel channel) throws IOException;

    void read(ChannelContext context, ByteBuf byteBuf);

    void accept(ChannelContext context) throws IOException;

    void exception(ChannelWrapper channelWrapper, Throwable cause);

    void userEventTriggered(ChannelWrapper channelWrapper, Object evt);

    void handleAdd(EventHandler handler);

    void handleRemove(ChannelContainer context);

    public EventHandler nextHandler();

    void exception(ChannelContext context, Throwable e);

    void write(ChannelContext ctx, T out);
}
