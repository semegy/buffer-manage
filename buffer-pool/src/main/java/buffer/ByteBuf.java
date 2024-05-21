package buffer;


import java.io.IOException;
import java.nio.*;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;

public interface ByteBuf<T> {
    void reuse(int maxCapacity);

    /**
     * 释放资源
     */
    void deallocate();

    ByteBuf slice();

    ByteBuffer slice(int index, int length);


    ByteBuf duplicate(int initialBytesToStrip, int frameLengthInt);

    ByteBuf asReadOnlyBuffer();

    byte get();

    public ByteBuf put(byte b);

    byte get(int index);

    ByteBuf put(int index, byte b);

    ByteBuf compact();

    boolean isReadOnly();

    boolean isDirect();

    char getChar();

    ByteBuf putChar(char value);

    char getChar(int index);

    ByteBuf putChar(int index, char value);

    CharBuffer asCharBuffer();

    short getShort();

    ByteBuf putShort(short value);

    short getShort(int index);

    ByteBuf putShort(int index, short value);

    ShortBuffer asShortBuffer();

    int getInt();

    ByteBuf putInt(int value);

    int getInt(int index);

    ByteBuf putInt(int index, int value);

    IntBuffer asIntBuffer();

    long getLong();

    ByteBuf putLong(long value);

    long getLong(int index);

    ByteBuf putLong(int index, long value);

    LongBuffer asLongBuffer();

    float getFloat();

    ByteBuf putFloat(float value);

    float getFloat(int index);

    ByteBuf putFloat(int index, float value);

    FloatBuffer asFloatBuffer();

    double getDouble();

    ByteBuf putDouble(double value);

    double getDouble(int index);

    ByteBuf putDouble(int index, double value);

    DoubleBuffer asDoubleBuffer();

    /**
     * 可写字段
     */
    int writableBytes();

    /**
     * 获取可读字节
     */
    int readableBytes();

    int capacity();

    // 写入buffer
    int writeBytes(ReadableByteChannel in, int length) throws IOException;

    ByteBuf readBytes(byte[] dst);

    ByteBuf readBytes(byte[] dst, int dstIndex, int length);

    int readIndex();

    void setReadIndex(int lengthFieldEndOffset);

    void setWriterIndex(int frameLengthInt);

    void setLength(int length);

    byte[] readBytes();

    void skip(int readerIndex);

    void add(ByteBuf buffer);

    void add(ByteBuf wrapper, int initialBytesToStrip);

    ByteBuf writeByte(int index, byte value);

    ByteBuf writeShort(int index, short value);

    ByteBuf writeInt(int index, int value);

    ByteBuf writeLong(int index, long value);

    ByteBuf writeBytes(byte[] dst, int i, int length);

    ByteBuf writeAndFlush(SocketChannel channel);

}
