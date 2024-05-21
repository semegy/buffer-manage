package channel.message;

import buffer.ByteBuf;
import channel.ChannelContext;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public abstract class ByteToMessageDecoder extends AbstractEventHandler {
    Logger log = Logger.getLogger(ByteToMessageDecoder.class.getName());
    // 允许的最大帧长度
    int maxFrameLength;
    // 长度字段在帧中的偏移量
    int lengthFieldOffset;
    // 长度字段的长度
    int lengthFieldLength;
    // 对长度字段值的调整量
    int lengthAdjustment;
    // 解码后要从帧前剥离的初始字节数
    int initialBytesToStrip;
    // 长度域偏移量
    int lengthFieldEndOffset;

    ByteBuf accumulationData;

    public ByteToMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        this.maxFrameLength = maxFrameLength;
        this.lengthFieldOffset = lengthFieldOffset;
        this.lengthFieldLength = lengthFieldLength;
        this.lengthAdjustment = lengthAdjustment;
        this.initialBytesToStrip = initialBytesToStrip;
        this.lengthFieldEndOffset = lengthFieldOffset + lengthFieldLength;
    }

    public void decode(ChannelContext context, ByteBuf buffer) {
        List<ByteBuf> byteBufList = decode0(buffer);
        handle(context, byteBufList);
    }

    protected void handle(ChannelContext context, List<ByteBuf> byteBufList) {
        for (ByteBuf byteBuf : byteBufList) {
            handle(context, byteBuf);
        }
    }

    protected abstract void handle(ChannelContext context, ByteBuf byteBuf);

    public List<ByteBuf> decode0(ByteBuf buffer) {
        List<ByteBuf> buffers = new ArrayList<>();
        for (; ; ) {
            int limit = buffer.capacity();
            if (accumulationData != null) {
                if (accumulationData.capacity() + buffer.capacity() > maxFrameLength) {
                    throw new IllegalArgumentException("frame length exceed maxFrameLength");
                }
                limit = accumulationData.capacity() - initialBytesToStrip;
                long frameLength = frameLength(accumulationData);
                int capacity = buffer.capacity();
                if (limit + capacity < frameLength) {
                    accumulationData.add(buffer.duplicate(0, capacity));
                    break;
                } else {
                    ByteBuf wrapper = buffer.duplicate(0, (int) frameLength - limit);
                    accumulationData.add(wrapper, initialBytesToStrip);
                    buffer.skip((int) frameLength - limit);
                    buffers.add(accumulationData);
                    accumulationData = null;
                    continue;
                }
            }
            if (limit < lengthFieldEndOffset) {
                break;
            }
            long frameLength = frameLength(buffer);
            if (frameLength < 0) {
                buffer.setReadIndex(lengthFieldEndOffset);
                throw new IllegalArgumentException("negative pre-adjustment length field: " + frameLength);
            }
            frameLength += lengthAdjustment + lengthFieldEndOffset;
            if (frameLength < lengthFieldEndOffset) {
                buffer.setReadIndex(lengthFieldEndOffset);
                throw new IllegalArgumentException(
                        "Adjusted frame length (" + frameLength + ") is less " +
                                "than lengthFieldEndOffset: " + lengthFieldEndOffset);
            }

            if (frameLength > maxFrameLength) {
                buffer.setReadIndex(maxFrameLength);
                if (frameLength > 0) {
                    throw new IllegalArgumentException(
                            "Adjusted frame length exceeds " + maxFrameLength +
                                    ": " + frameLength + " - discarded");
                } else {
                    throw new IllegalArgumentException(
                            "Adjusted frame length exceeds " + maxFrameLength +
                                    " - discarding");
                }
            }
            int frameLengthInt = (int) frameLength;

            if (limit < frameLengthInt) { // frameLengthInt exist , just check buf
                accumulationData = buffer.duplicate(0, limit);
                break;
            }

            if (initialBytesToStrip > frameLengthInt) {
                buffer.setReadIndex(frameLengthInt);
                throw new IllegalArgumentException(
                        "Adjusted frame length (" + frameLength + ") is less " +
                                "than initialBytesToStrip: " + initialBytesToStrip);
            }

            ByteBuf wrapper = buffer.duplicate(initialBytesToStrip, frameLengthInt);
            buffer.skip(frameLengthInt);
            buffers.add(wrapper);
        }
        return buffers;
    }

    private long frameLength(ByteBuf buffer) {
        long frameLength;

        switch (lengthFieldLength) {
            case 1:
                frameLength = 0xff & buffer.get(lengthFieldOffset);
                break;
            case 2:
                frameLength = 0xffff & buffer.getShort(lengthFieldOffset);
                break;
            case 4:
                frameLength = buffer.getInt(lengthFieldOffset);
                break;
            case 8:
                frameLength = buffer.getLong(lengthFieldOffset);
                break;
            default:
                throw new IllegalArgumentException("unsupported lengthFieldLength: " + lengthFieldLength + " (expected: 1, 2, 3, 4, or 8)");
        }
        return frameLength;
    }

    @Override
    public void connect(SocketChannel channel) throws IOException {

    }

    @Override
    public void read(ChannelContext context, ByteBuf byteBuf) {
        decode(context, byteBuf);
    }


}
