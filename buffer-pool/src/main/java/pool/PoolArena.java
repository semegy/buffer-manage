package pool;

import pool.recycle.Recycler;
import pool.recycle.ThreadLocalCache;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static pool.PoolChunk.isSubpage;

public class PoolArena<T> extends SizeClasses {

    public AtomicInteger numThreadCaches = new AtomicInteger();

    private int deallocationsNormal;
    private int deallocationsSmall;

    public <T> void free(PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, int normCapacity, ThreadLocalCache cache) {
        SizeClass sizeClass = sizeClass(handle);
        if (cache != null && cache.add(this, chunk, nioBuffer, handle, normCapacity, sizeClass)) {
            return;
        }
        // 释放chunk空间
        freeChunk(chunk, handle, normCapacity, sizeClass, nioBuffer, false);
    }

    private SizeClass sizeClass(long handle) {
        return isSubpage(handle) ? SizeClass.Small : SizeClass.Normal;
    }

    public enum SizeClass {
        Small,
        Normal
    }

    public Recycler<PooledByteBuf<T>> recycler = new Recycler<PooledByteBuf<T>>() {
        protected PooledByteBuf<T> newObject(Recycler.Handle handle) {
            return new PooledByteBuf<T>(handle);
        }
    };


    final int nPSizes;
    public int numSmallSubpagePools;
    PoolSubpage<T>[] smallSubpagePools;

    public PoolArena(int pageSize, int pageShifts, int chunkSize, int cacheAlignment) {
        super(pageSize, pageShifts, chunkSize, cacheAlignment);
        this.nPSizes = 40;
        numSmallSubpagePools = nSubpages;
        smallSubpagePools = newSubpagePoolArray(nSubpages);
    }

    private PoolSubpage<T> newSubpagePoolHead() {
        PoolSubpage<T> head = new PoolSubpage<T>();
        head.prev = head;
        head.next = head;
        return head;
    }

    private PoolSubpage<T>[] newSubpagePoolArray(int nSubpages) {
        PoolSubpage[] poolSubpages = new PoolSubpage[nSubpages];
        for (int i = 0; i < nSubpages; i++) {
            poolSubpages[i] = newSubpagePoolHead();
        }
        return poolSubpages;
    }

    public ByteBuf allocate(ThreadLocalCache cache, int reqCapacity, int maxCapacity) {
        // 先从缓存池中获取到合适容量的PooledByteBuf, 没有则创建新的buf
        PooledByteBuf<T> buf = newInstance(maxCapacity);
        // buf分配空间
        allocate(cache, buf, reqCapacity);
        return buf;
    }

    private void allocate(ThreadLocalCache cache, PooledByteBuf<T> buf, int reqCapacity) {
        // 原理 根据请求的空间大小找到合适sizeIdx （寻址索引）
        final int sizeIdx = size2SizeIdx(reqCapacity);
        allocateNormal(cache, buf, reqCapacity, sizeIdx);
    }

    private PooledByteBuf<T> newInstance(int maxCapacity) {
        PooledByteBuf buf = recycler.get();
        buf.reused(maxCapacity);
        return buf;
    }

    private void allocateNormal(ThreadLocalCache cache, PooledByteBuf<T> buf, int reqCapacity, int sizeIdx) {
        if (cache.allocateNormal(this, buf, sizeIdx)) {
            return;
        }
        // Add a new chunk.
        ByteBuffer memory = ByteBuffer.allocateDirect(reqCapacity);
        PoolChunk poolChunk = newChunk(this, memory);
        poolChunk.allocate(buf, reqCapacity, sizeIdx, cache);
    }

    PoolChunk<ByteBuffer> newChunk(PoolArena arena, ByteBuffer memory) {
        return new PoolChunk<ByteBuffer>(this, memory);
    }

    public void freeChunk(PoolChunk chunk, long handle, int normCapacity, SizeClass sizeClass, ByteBuffer nioBuffer, boolean finalizer) {
        final boolean destroyChunk;
        synchronized (this) {
            // We only call this if freeChunk is not called because of the PoolThreadCache finalizer as otherwise this
            // may fail due lazy class-loading in for example tomcat.
            if (!finalizer) {
                switch (sizeClass) {
                    case Normal:
                        ++deallocationsNormal;
                        break;
                    case Small:
                        ++deallocationsSmall;
                        break;
                    default:
                        throw new Error();
                }
            }
            // 销毁chunk?
//            destroyChunk = !chunk.parent.free(chunk, handle, normCapacity, nioBuffer);
        }
//        if (destroyChunk) {
//            // destroyChunk not need to be called while holding the synchronized lock.
//            // 销毁chunk,创建新的PoolChunk
//            // todo
////            destroyChunk(chunk);
//        }

    }


}
