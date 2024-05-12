package buffer;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.*;
import java.nio.channels.ReadableByteChannel;

public interface ByteBuf<T> {
    void reused(int maxCapacity);

    /**
     * 释放资源
     */
    void deallocate();

    ByteBuf slice();

    ByteBuf duplicate();

    ByteBuf asReadOnlyBuffer();

    byte get();

    ByteBuf put(byte b);

    byte get(int index);

    ByteBuf put(int index, byte b);

    @NotNull ByteBuf compact();

    boolean isReadOnly();

    boolean isDirect();

    char getChar();

    @NotNull ByteBuf putChar(char value);

    char getChar(int index);

    @NotNull ByteBuf putChar(int index, char value);

    @NotNull CharBuffer asCharBuffer();

    short getShort();

    @NotNull ByteBuf putShort(short value);

    short getShort(int index);

    @NotNull ByteBuf putShort(int index, short value);

    @NotNull ShortBuffer asShortBuffer();

    int getInt();

    @NotNull ByteBuf putInt(int value);

    int getInt(int index);

    @NotNull ByteBuf putInt(int index, int value);

    @NotNull IntBuffer asIntBuffer();

    long getLong();

    @NotNull ByteBuf putLong(long value);

    long getLong(int index);

    @NotNull ByteBuf putLong(int index, long value);

    @NotNull LongBuffer asLongBuffer();

    float getFloat();

    @NotNull ByteBuf putFloat(float value);

    float getFloat(int index);

    @NotNull ByteBuf putFloat(int index, float value);

    @NotNull FloatBuffer asFloatBuffer();

    double getDouble();

    @NotNull ByteBuf putDouble(double value);

    double getDouble(int index);

    @NotNull ByteBuf putDouble(int index, double value);

    @NotNull DoubleBuffer asDoubleBuffer();

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

}
