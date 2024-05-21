package channel.message;

import buffer.ByteBuf;
import channel.ChannelContext;

public class StringDecoder extends ByteToMessageDecoder {
    public StringDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected void handle(ChannelContext context, ByteBuf byteBuf) {
        byte[] bytes = byteBuf.readBytes();
        log.info(new String(bytes));
        context.write(new String(bytes));
    }


}
