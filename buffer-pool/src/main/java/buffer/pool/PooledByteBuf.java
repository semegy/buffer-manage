package buffer.pool;

import buffer.AbstractByteBuf;
import buffer.ByteBuf;
import buffer.recycle.Recycler;
import buffer.recycle.ThreadLocalCache;

import java.nio.ByteBuffer;

public abstract class PooledByteBuf<T> extends AbstractByteBuf<T> {
    PoolChunk<T> chunk;
    T memory;

    int offset;
    long handle;

    protected int length;
    int maxLength;

    private int maxCapacity;

    public ThreadLocalCache cache;

    private PooledBufferAllocate allocator;

    final Recycler.Handle<PooledByteBuf<T>> recyclerHandle;

    public PooledByteBuf(Recycler.Handle recyclerHandle) {
        this.recyclerHandle = recyclerHandle;
    }

    void init(PoolChunk<T> chunk, ByteBuffer nioBuffer,
              long handle, int offset, int length, int maxLength, ThreadLocalCache cache) {
        init0(chunk, nioBuffer, handle, offset, length, maxLength, cache);
    }

    void initUnpooled(PoolChunk<T> chunk, int reqCapacity) {
        init0(chunk, null, 0, 0, length, length, null);
    }

    private void init0(PoolChunk<T> chunk, ByteBuffer nioBuffer,
                       long handle, int offset, int length, int maxLength, ThreadLocalCache cache) {

        chunk.incrementPinnedMemory(maxLength);
        this.chunk = chunk;
        memory = chunk.memory;
        buffer = nioBuffer;
        allocator = chunk.arena.parent;
        this.cache = cache;
        this.handle = handle;
        this.offset = offset;
        this.length = length;
        this.maxLength = maxLength;
    }

    public final void reuse(int maxCapacity) {
        maxCapacity(maxCapacity);
        setIndex0(0, 0);
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
            chunk.arena.free(chunk, buffer, handle, maxLength, cache);
            buffer = null;
            chunk = null;
            recycle();
        }
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
//        checkDstIndex(length, dstIndex, dst.length);
        _internalNioBuffer(this.readerIndex, length, false).get(dst, dstIndex, length);
        readerIndex += length;
        return this;
    }

    public void lastBytesRead() {
    }

    @Override
    public final ByteBuffer internalNioBuffer(int index, int length) {
//        checkIndex(index, length);
        return _internalNioBuffer(index, length, false);
    }

    public final ByteBuffer _internalNioBuffer(int index, int length, boolean duplicate) {
        ByteBuffer buffer = duplicate ? newInternalNioBuffer(memory) : internalNioBuffer();
        return buffer;
    }

    protected final ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.buffer;
        if (tmpNioBuf == null) {
            this.buffer = tmpNioBuf = newInternalNioBuffer(memory);
        } else {
            tmpNioBuf.clear();
        }
        return tmpNioBuf;
    }

    @Override
    public ByteBuf writeBytes(byte[] dst, int index, int length) {
        ByteBuffer byteBuffer = internalNioBuffer(index, length);
        byteBuffer.position(index);
        byteBuffer.limit(index + length);
        this.buffer.put(dst);
        return this;
    }


    private int internalPosition(int index) {
        return index;
    }

}
