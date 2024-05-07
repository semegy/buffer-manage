package pool;

import pool.recycle.Recycler;

import java.nio.ByteBuffer;

public class PooledByteBuf<T> implements ByteBuf {

    private PoolChunk<T> chunk;


    private final Recycler.Handle<PooledByteBuf<T>> recyclerHandle;
    private long handle;
    private int offset;
    private int length;
    private int maxLength;

    private int maxCapacity;

    public PooledByteBuf(Recycler.Handle recyclerHandle) {
        this.recyclerHandle = recyclerHandle;
    }

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

    @Override
    public void reused(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public void recycle() {
        recyclerHandle.recycle(this);
    }

}
