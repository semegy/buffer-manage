package channel.message;

import buffer.ByteBuf;
import channel.ChannelContext;

import static channel.ChannelContainer.bufferAllocate;

public class StringEncoder extends MessageToByteEncoder<String> {

    public StringEncoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected ByteBuf encode(ChannelContext ctx, String out) {
        byte[] bytes = out.getBytes();
        ByteBuf buf = bufferAllocate.ioBuffer(bytes.length + initialBytesToStrip);
        try {
            buf.writeBytes(bytes, lengthFieldLength, bytes.length);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        buf.setReadIndex(initialBytesToStrip);
        buf.setWriterIndex(bytes.length + initialBytesToStrip);
        buf.setLength(bytes.length);
        super.encode(ctx, buf);
        return buf;
    }
}
