package buffer;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.*;
import java.nio.channels.ReadableByteChannel;


public abstract class AbstractByteBuf<T> implements ByteBuf {
    protected ByteBuffer buffer;
    protected WrapperByteBuf root = null;

    // 读索引
    public int readerIndex;
    // 写索引
    public int writerIndex;

    // 标记最大容量
    private int maxCapacity;

    public AbstractByteBuf(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void reused(int maxCapacity) {

    }

    @Override
    public void deallocate() {

    }

    public AbstractByteBuf() {
    }

    @Override
    public ByteBuf slice() {
        ByteBuffer newBuffer = this.buffer.slice();
        return root.wrapperBuffer(newBuffer);
    }

    @Override
    public ByteBuf duplicate() {
        ByteBuffer newBuffer = this.buffer.duplicate();
        return wrapperBuffer(newBuffer);
    }

    ByteBuf wrapperBuffer(ByteBuffer newBuffer) {
        return root.wrapperBuffer(newBuffer);
    }

    @Override
    public ByteBuf asReadOnlyBuffer() {
        ByteBuffer newBuffer = this.buffer.duplicate();
        return wrapperBuffer(newBuffer);
    }

    @Override
    public byte get() {
        return buffer.get();
    }

    @Override
    public ByteBuf put(byte b) {
        ByteBuffer newBuffer = this.buffer.put(b);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public byte get(int index) {
        return buffer.get(index);
    }

    @Override
    public ByteBuf put(int index, byte b) {
        ByteBuffer newBuffer = buffer.put(index, b);
        return wrapperBuffer(newBuffer);
    }

    @NotNull
    @Override
    public ByteBuf compact() {
        ByteBuffer newBuffer = buffer.compact();
        return wrapperBuffer(newBuffer);
    }

    @Override
    public boolean isReadOnly() {
        return buffer.isReadOnly();
    }

    @Override
    public boolean isDirect() {
        return buffer.isDirect();
    }

    @Override
    public char getChar() {
        return buffer.getChar();
    }

    @NotNull
    @Override
    public ByteBuf putChar(char value) {
        ByteBuffer newBuffer = buffer.putChar(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public char getChar(int index) {
        return buffer.getChar(index);
    }

    @NotNull
    @Override
    public ByteBuf putChar(int index, char value) {
        ByteBuffer newBuffer = buffer.putChar(index, value);
        return wrapperBuffer(newBuffer);
    }

    @NotNull
    @Override
    public CharBuffer asCharBuffer() {
        return buffer.asCharBuffer();
    }

    @Override
    public short getShort() {
        return buffer.getShort();
    }

    @NotNull
    @Override
    public ByteBuf putShort(short value) {
        ByteBuffer newBuffer = buffer.putShort(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public short getShort(int index) {
        return buffer.getShort(index);
    }

    @NotNull
    @Override
    public ByteBuf putShort(int index, short value) {
        buffer.putShort(index, value);
        return this;
    }

    @NotNull
    @Override
    public ShortBuffer asShortBuffer() {
        return buffer.asShortBuffer();
    }

    @Override
    public int getInt() {
        return buffer.getInt();
    }

    @NotNull
    @Override
    public ByteBuf putInt(int value) {
        ByteBuffer newBuffer = buffer.putInt(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public int getInt(int index) {
        return buffer.getInt(index);
    }

    @NotNull
    @Override
    public ByteBuf putInt(int index, int value) {
        ByteBuffer newBuffer = buffer.putInt(index, value);
        return wrapperBuffer(newBuffer);
    }

    @NotNull
    @Override
    public IntBuffer asIntBuffer() {
        return buffer.asIntBuffer();
    }

    @Override
    public long getLong() {
        return buffer.getLong();
    }

    @NotNull
    @Override
    public ByteBuf putLong(long value) {
        ByteBuffer newBuffer = buffer.putLong(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public long getLong(int index) {
        return buffer.getLong(index);
    }

    @NotNull
    @Override
    public ByteBuf putLong(int index, long value) {
        ByteBuffer newBuffer = buffer.putLong(index, value);
        return wrapperBuffer(newBuffer);
    }

    @NotNull
    @Override
    public LongBuffer asLongBuffer() {
        return buffer.asLongBuffer();
    }

    @Override
    public float getFloat() {
        return buffer.getFloat();
    }

    @NotNull
    @Override
    public ByteBuf putFloat(float value) {
        ByteBuffer newBuffer = buffer.putFloat(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public float getFloat(int index) {
        return buffer.getFloat(index);
    }

    @NotNull
    @Override
    public ByteBuf putFloat(int index, float value) {
        ByteBuffer newBuffer = buffer.putFloat(index, value);
        return wrapperBuffer(newBuffer);
    }

    @NotNull
    @Override
    public FloatBuffer asFloatBuffer() {
        return buffer.asFloatBuffer();
    }

    @Override
    public double getDouble() {
        return buffer.getDouble();
    }

    @NotNull
    @Override
    public ByteBuf putDouble(double value) {
        ByteBuffer newBuffer = buffer.putDouble(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public double getDouble(int index) {
        return buffer.getDouble(index);
    }

    @NotNull
    @Override
    public ByteBuf putDouble(int index, double value) {
        ByteBuffer newBuffer = buffer.putDouble(index, value);
        return wrapperBuffer(newBuffer);
    }

    @NotNull
    @Override
    public DoubleBuffer asDoubleBuffer() {
        return buffer.asDoubleBuffer();
    }

    @Override
    public int writableBytes() {
        return this.capacity() - this.writerIndex;
    }

    @Override
    public int readableBytes() {
        return this.writerIndex - this.readerIndex;
    }

    @Override
    public int writeBytes(ReadableByteChannel in, int length) throws IOException {
        // ensureWritable(length);
        int writtenBytes = in.read(internalNioBuffer(writerIndex, length));
        if (writtenBytes > 0) {
            writerIndex += writtenBytes;
        }
        return writtenBytes;
    }


    @Override
    public ByteBuf readBytes(byte[] dst) {
        ByteBuf byteBuf = readBytes(dst, 0, dst.length);
        return byteBuf;
    }

    protected abstract ByteBuffer newInternalNioBuffer(T memory);


    public abstract ByteBuffer internalNioBuffer(int index, int length);

    protected abstract ByteBuffer _internalNioBuffer(int index, int length, boolean duplicate);

    protected abstract ByteBuffer newInternalNioBuffer(ByteBuffer memory);
}
