package buffer;

import java.nio.ByteBuffer;

public class WrapperByteBuf extends AbstractByteBuf {

    public WrapperByteBuf(ByteBuffer buffer) {
        super(buffer);
    }

    ByteBuf wrapperBuffer(ByteBuffer newBuffer) {
        this.buffer = buffer;
        return this;
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
    public int capacity() {
        return 0;
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        return null;
    }
}
