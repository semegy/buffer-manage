package buffer;


import java.io.IOException;
import java.nio.*;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;


public abstract class AbstractByteBuf<T> implements ByteBuf {
    protected ByteBuffer buffer;
    static protected WrapperByteBuf root = new WrapperByteBuf();

    // 读索引
    public int readerIndex;
    // 写索引
    public int writerIndex;

    // 标记最大容量
    private int maxCapacity;

    public AbstractByteBuf(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    protected final void maxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public final void setIndex0(int readerIndex, int writerIndex) {
        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
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
    public ByteBuffer slice(int index, int length) {
        ByteBuffer byteBuffer = internalNioBuffer(index, length);
        return byteBuffer;
    }

    public byte[] readBytes() {
        byte[] dst = new byte[capacity()];
        this.buffer.position(readerIndex);
        this.buffer.limit(writerIndex);
        this.buffer.get(dst);
        return dst;
    }

    @Override
    public void skip(int readerIndex) {
        this.readerIndex += readerIndex;
        setLength(writerIndex - this.readerIndex);
        this.buffer.position(readerIndex);
    }

    @Override
    public void add(ByteBuf buffer) {

    }

    @Override
    public void add(ByteBuf buffer, int initialBytesToStrip) {

    }

    @Override
    public int readIndex() {
        return readerIndex;
    }

    public void setReadIndex(int readerIndex) {
        this.readerIndex = readerIndex;
    }

    public void setWriterIndex(int writerIndex) {
        this.writerIndex = writerIndex;
    }

    @Override
    public ByteBuf duplicate(int initialBytesToStrip, int frameLengthInt) {
        ByteBuffer newBuffer = this.buffer.duplicate();
        ByteBuf wrapperBuffer = wrapperBuffer(newBuffer);
        wrapperBuffer.setReadIndex(this.readerIndex + initialBytesToStrip);
        wrapperBuffer.setWriterIndex(this.readerIndex + initialBytesToStrip + frameLengthInt);
        wrapperBuffer.setLength(frameLengthInt - initialBytesToStrip);
        return wrapperBuffer;
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
        return buffer.get(this.readIndex() + index);
    }

    @Override
    public ByteBuf put(int index, byte b) {
        ByteBuffer newBuffer = buffer.put(index, b);
        return wrapperBuffer(newBuffer);
    }

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

    @Override
    public ByteBuf putChar(char value) {
        ByteBuffer newBuffer = buffer.putChar(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public char getChar(int index) {
        return buffer.getChar(index);
    }

    @Override
    public ByteBuf putChar(int index, char value) {
        ByteBuffer newBuffer = buffer.putChar(index, value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public CharBuffer asCharBuffer() {
        return buffer.asCharBuffer();
    }

    @Override
    public short getShort() {
        return buffer.getShort();
    }

    @Override
    public ByteBuf putShort(short value) {
        ByteBuffer newBuffer = buffer.putShort(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public short getShort(int index) {
        return buffer.getShort(this.readerIndex + index);
    }

    @Override
    public ByteBuf putShort(int index, short value) {
        buffer.putShort(index, value);
        return this;
    }

    @Override
    public ShortBuffer asShortBuffer() {
        return buffer.asShortBuffer();
    }

    @Override
    public int getInt() {
        return buffer.getInt();
    }

    @Override
    public ByteBuf putInt(int value) {
        ByteBuffer newBuffer = buffer.putInt(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public int getInt(int index) {
        return buffer.getInt(this.readerIndex + index);
    }

    @Override
    public ByteBuf putInt(int index, int value) {
        ByteBuffer newBuffer = buffer.putInt(index, value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public IntBuffer asIntBuffer() {
        return buffer.asIntBuffer();
    }

    @Override
    public long getLong() {
        return buffer.getLong();
    }

    @Override
    public ByteBuf putLong(long value) {
        ByteBuffer newBuffer = buffer.putLong(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public long getLong(int index) {
        return buffer.getLong(readerIndex + index);
    }

    @Override
    public ByteBuf putLong(int index, long value) {
        ByteBuffer newBuffer = buffer.putLong(index, value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public LongBuffer asLongBuffer() {
        return buffer.asLongBuffer();
    }

    @Override
    public float getFloat() {
        return buffer.getFloat();
    }

    @Override
    public ByteBuf putFloat(float value) {
        ByteBuffer newBuffer = buffer.putFloat(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public float getFloat(int index) {
        return buffer.getFloat(index);
    }

    @Override
    public ByteBuf putFloat(int index, float value) {
        ByteBuffer newBuffer = buffer.putFloat(index, value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public FloatBuffer asFloatBuffer() {
        return buffer.asFloatBuffer();
    }

    @Override
    public double getDouble() {
        return buffer.getDouble();
    }

    @Override
    public ByteBuf putDouble(double value) {
        ByteBuffer newBuffer = buffer.putDouble(value);
        return wrapperBuffer(newBuffer);
    }

    @Override
    public double getDouble(int index) {
        return buffer.getDouble(index);
    }

    @Override
    public ByteBuf putDouble(int index, double value) {
        ByteBuffer newBuffer = buffer.putDouble(index, value);
        return wrapperBuffer(newBuffer);
    }

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
        setLength(writerIndex - readerIndex);
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

    public ByteBuf writeByte(int index, byte value) {
        this.buffer.put(index, value);
        return this;
    }

    public ByteBuf writeShort(int index, short value) {
        this.buffer.putShort(index, value);
        return this;
    }

    public ByteBuf writeInt(int index, int value) {
        this.buffer.putInt(index, value);
        return this;
    }

    public ByteBuf writeLong(int index, long value) {
        this.buffer.putLong(index, value);
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] dst, int index, int length) {
        this.buffer.position(index);
        this.buffer.put(dst, index, length);
        return this;
    }

    @Override
    public ByteBuf writeAndFlush(SocketChannel channel) {
        this.buffer.position(0);
        this.buffer.limit(writerIndex);
        try {
            channel.write(buffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
}
