package channel.message;

import buffer.ByteBuf;
import channel.ChannelContainer;
import channel.ChannelContext;
import channel.ChannelWrapper;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public abstract class MessageToByteEncoder<T> extends AbstractEventHandler<T> {

    Logger log = Logger.getLogger(MessageToByteEncoder.class.getName());
    // 允许的最大帧长度
    int maxFrameLength;
    // 长度字段在帧中的偏移量
    int lengthFieldOffset;
    // 长度字段的长度
    public int lengthFieldLength;
    // 对长度字段值的调整量
    int lengthAdjustment;
    // 解码后要从帧前剥离的初始字节数
    public int initialBytesToStrip;


    public MessageToByteEncoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        this.maxFrameLength = maxFrameLength;
        this.lengthFieldOffset = lengthFieldOffset;
        this.lengthFieldLength = lengthFieldLength;
        this.lengthAdjustment = lengthAdjustment;
        this.initialBytesToStrip = initialBytesToStrip;
    }

    @Override
    public void connect(SocketChannel channel) throws IOException {

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

    @Override
    public void write(ChannelContext ctx, T out) {
        ByteBuf buf = encode(ctx, out);
        super.write(ctx, buf);
    }

    protected abstract ByteBuf encode(ChannelContext ctx, T out);

    protected final void encode(ChannelContext ctx, ByteBuf out) {
        writeLength(out);
    }

    private void writeLength(ByteBuf out) {
        int capacity = out.capacity();
        switch (lengthFieldLength) {
            case 1:
                out.writeByte(0, (byte) capacity);
                break;
            case 2:
                out.writeShort(0, (short) capacity);
                break;
            case 4:
                out.writeInt(0, (int) capacity);
                break;
            case 8:
                out.writeLong(0, (long) capacity);
                break;
            default:
                throw new IllegalArgumentException("lengthFieldOffset must be either 1, 2, 3, 4, or 8");
        }
    }
}
