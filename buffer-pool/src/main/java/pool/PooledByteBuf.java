package pool;

import java.nio.ByteBuffer;

public class PooledByteBuf<T> {

    private PoolChunk<T> chunk;
    private long handle;
    private int offset;
    private int length;
    private int maxLength;

    void init(PoolChunk<T> chunk, ByteBuffer nioBuffer,
              long handle, int offset, int length, int maxLength) {
        init0(chunk, nioBuffer, handle, offset, length, maxLength);
    }

    private void init0(PoolChunk<T> chunk, ByteBuffer nioBuffer,
                       long handle, int offset, int length, int maxLength) {

//        chunk.incrementPinnedMemory(maxLength);
        this.chunk = chunk;
        T memory = chunk.memory;
        ByteBuffer tmpNioBuf = nioBuffer;
//        Object allocator = chunk.arena.parent;
        this.handle = handle;
        this.offset = offset;
        this.length = length;
        this.maxLength = maxLength;
    }
}
