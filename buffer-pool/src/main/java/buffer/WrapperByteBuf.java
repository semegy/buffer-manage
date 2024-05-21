package buffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class WrapperByteBuf extends AbstractByteBuf {

    int length;
    List<ByteBuf> byteBufList = new ArrayList<>();

    public WrapperByteBuf() {
    }

    public WrapperByteBuf(ByteBuffer buffer) {
        super(buffer);
    }

    ByteBuf wrapperBuffer(ByteBuffer newBuffer) {
        return new WrapperByteBuf(newBuffer);
    }

    @Override
    protected ByteBuffer newInternalNioBuffer(Object memory) {
        return null;
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return null;
    }

    @Override
    protected ByteBuffer _internalNioBuffer(int index, int length, boolean duplicate) {
        return null;
    }

    @Override
    protected ByteBuffer newInternalNioBuffer(ByteBuffer memory) {
        return null;
    }

    @Override
    public void reuse(int maxCapacity) {

    }

    @Override
    public int capacity() {
        return length;
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        buffer.position(readerIndex);
        buffer.get(dst, dstIndex, length);
        return this;
    }

    @Override
    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public void add(ByteBuf buffer) {
        byteBufList.add(buffer);
        this.length += buffer.capacity();
    }

    @Override
    public void add(ByteBuf buffer, int initialBytesToStrip) {
        setReadIndex(readerIndex + initialBytesToStrip);
        byteBufList.add(buffer);
        this.length += buffer.capacity() - initialBytesToStrip;
    }

    public byte[] readBytes() {
        if (byteBufList.isEmpty()) {
            return super.readBytes();
        } else {
            byte[] dst = new byte[capacity()];
            this.buffer.position(readerIndex);
            this.buffer.limit(writerIndex);
            int readAble = writerIndex - readerIndex;
            this.buffer.get(dst, 0, readAble);
            int index = readAble;
            for (ByteBuf byteBuf : byteBufList) {
                byteBuf.readBytes(dst, index, byteBuf.capacity());
                index += byteBuf.capacity();
            }
            return dst;
        }
    }
}
