package pool;

import pool.recycle.Recycler;
import pool.recycle.ThreadLocalCache;

import java.nio.ByteBuffer;

public class PooledByteBuf<T> implements ByteBuf {

    private PoolChunk<T> chunk;
    private final Recycler.Handle<PooledByteBuf<T>> recyclerHandle;
    private long handle;
    private int offset;
    private int length;
    private int maxLength;

    private int maxCapacity;

    private T memory;

    private ByteBuffer tmpNioBuf;
    private ThreadLocalCache cache;

    private PooledBufferAllocate allocator;

    public PooledByteBuf(Recycler.Handle recyclerHandle) {
        this.recyclerHandle = recyclerHandle;
    }

    void init(PoolChunk<T> chunk, ByteBuffer nioBuffer,
              long handle, int offset, int length, int maxLength, ThreadLocalCache cache) {
        init0(chunk, nioBuffer, handle, offset, length, maxLength, cache);
    }

    private void init0(PoolChunk<T> chunk, ByteBuffer nioBuffer,
                       long handle, int offset, int length, int maxLength, ThreadLocalCache cache) {

        chunk.incrementPinnedMemory(maxLength);
        this.chunk = chunk;
        memory = chunk.memory;
        tmpNioBuf = nioBuffer;
        allocator = chunk.arena.parent;
        this.cache = cache;
        this.handle = handle;
        this.offset = offset;
        this.length = length;
        this.maxLength = maxLength;
    }

    @Override
    public void reused(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    private void recycle() {
        recyclerHandle.recycle(this);
    }

    @Override
    // 解除分配
    public final void deallocate() {
        if (handle >= 0) {
            final long handle = this.handle;
            this.handle = -1;
            memory = null;
            chunk.decrementPinnedMemory(maxLength);
            chunk.arena.free(chunk, tmpNioBuf, handle, maxLength, cache);
            tmpNioBuf = null;
            chunk = null;
            recycle();
        }
    }

}
