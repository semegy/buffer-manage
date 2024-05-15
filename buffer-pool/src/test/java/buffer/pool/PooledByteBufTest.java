package buffer.pool;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class PooledByteBufTest {

    @Test
    public void testReadBytes() {
        int reqCapacity = 1024 * 30;
        int maxCapacity = Integer.MAX_VALUE;
        PooledBufferAllocate bufferAllocate = new PooledBufferAllocate(32, 8192, 11, 256, 64, true);
        PooledByteBuf allocate = (PooledByteBuf) bufferAllocate.allocate();
        byte[] dst = new byte[10];
        int dstIndex = 0;
        int length = dst.length;

        // Initialize the buffer with some data
        for (int i = 0; i < dst.length; i++) {
            dst[i] = (byte) i;
        }
        ByteBuffer byteBuffer = allocate.internalNioBuffer(0, dst.length);
        byteBuffer.put(dst);
        allocate.writerIndex = dst.length;
        allocate.readerIndex = 0;

        byte[] bytes = new byte[dst.length];
        // Read bytes from the buffer
        allocate.readBytes(bytes, dstIndex, length);

        // Verify the read data
        for (int i = 0; i < bytes.length; i++) {
            assertEquals("Incorrect byte read at position " + i, (byte) i, bytes[i]);
        }
    }
}
