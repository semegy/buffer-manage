package channel.message;

import buffer.ByteBuf;
import channel.ChannelContainer;
import channel.ChannelContext;
import channel.ChannelWrapper;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

public class HeadHandler extends AbstractEventHandler {
    public static EventHandler TAIL_HANDLER = new TailHandler();

    public HeadHandler() {
        tailPer = this;
        tail = TAIL_HANDLER;
        nextHandler = tail;
    }

    @Override
    public void connect(SocketChannel channelWrapper) throws IOException {

    }

    @Override
    public void read(ChannelContext ctx, ByteBuf byteBuf) {
        SelectableChannel ch = ctx.channel();
        int writeAble;
        try {
            do {
                writeAble = doReadBytes(ch, byteBuf);
                if (writeAble == 0) {
                    break;
                }
                nextHandler().read(ctx, byteBuf);
                if (writeAble == -1) {
                    ch.close();
                    break;
                }
            } while (writeAble != 0);
        } catch (Exception e) {
            exception(ctx, e);
        }
    }

    final private int doReadBytes(SelectableChannel channel, ByteBuf byteBuf) {
        try {
            return byteBuf.writeBytes((SocketChannel) channel, byteBuf.writableBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
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
