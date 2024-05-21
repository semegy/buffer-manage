package buffer.pool;

import buffer.ByteBuf;
import buffer.recycle.Recycler;
import buffer.recycle.ThreadLocalCache;
import sun.nio.ch.DirectBuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class PoolArena<T> extends SizeClasses {

    public AtomicInteger numThreadCaches = new AtomicInteger();

    private int deallocationsNormal;
    private int deallocationsSmall;

    final PooledBufferAllocate parent;

    private final PoolChunkList<T> q050;
    private final PoolChunkList<T> q025;
    private final PoolChunkList<T> q000;
    private final PoolChunkList<T> qInit;
    private final PoolChunkList<T> q075;
    private final PoolChunkList<T> q100;

    private final LongAdder activeBytesHuge = new LongAdder();

    private final LongAdder allocationsHuge = new LongAdder();
    private final LongAdder allocationsSmall = new LongAdder();


    public <T> void free(PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, int normCapacity, ThreadLocalCache cache) {
        SizeClass sizeClass = sizeClass(handle);
        if (cache != null && cache.add(this, chunk, nioBuffer, handle, normCapacity, sizeClass)) {
            return;
        }
        // 释放chunk空间
        freeChunk(chunk, handle, normCapacity, sizeClass, nioBuffer, false);
    }

    private SizeClass sizeClass(long handle) {
        return PoolChunk.isSubpage(handle) ? SizeClass.Small : SizeClass.Normal;
    }

    PoolSubpage<T> findSubpagePoolHead(int sizeIdx) {
        return smallSubpagePools[sizeIdx];
    }

    public enum SizeClass {
        Small, Normal
    }

    public Recycler<PooledByteBuf> recycler = new Recycler() {
        protected PooleDirectByteBuf newObject(Handle handle) {
            return new PooleDirectByteBuf(handle);
        }
    };


    final int nPSizes;
    public int numSmallSubpagePools;
    PoolSubpage<T>[] smallSubpagePools;

    public PoolArena(int pageSize, int pageShifts, int chunkSize, PooledBufferAllocate parent, int cacheAlignment) {
        super(pageSize, pageShifts, chunkSize, cacheAlignment);
        this.nPSizes = 40;
        numSmallSubpagePools = nSubpages;
        smallSubpagePools = newSubpagePoolArray(nSubpages);
        this.parent = parent;

        q100 = new PoolChunkList<T>(this, null, 100, Integer.MAX_VALUE, chunkSize);
        q075 = new PoolChunkList<T>(this, q100, 75, 100, chunkSize);
        q050 = new PoolChunkList<T>(this, q075, 50, 100, chunkSize);
        q025 = new PoolChunkList<T>(this, q050, 25, 75, chunkSize);
        q000 = new PoolChunkList<T>(this, q025, 1, 50, chunkSize);
        qInit = new PoolChunkList<T>(this, q000, Integer.MIN_VALUE, 25, chunkSize);

        q100.prevList(q075);
        q075.prevList(q050);
        q050.prevList(q025);
        q025.prevList(q000);
        q000.prevList(null);
        qInit.prevList(qInit);
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
        // 相当于size小于28k
        if (sizeIdx <= smallMaxSizeIdx) {
            // 按页分配
            allocateSmall(cache, buf, reqCapacity, sizeIdx);
        } else if (sizeIdx < nSizes) { // chunk区大小不够 使用normal分配
            // normal
            allocateNormal(cache, buf, reqCapacity, sizeIdx);
        } else {
            int normCapacity = directMemoryCacheAlignment > 0 ? normalizeSize(reqCapacity) : reqCapacity;
            // Huge allocations are never served via the cache so just call allocateHuge
            // 大内存分配机制
            // 不会通过缓存直接分配
            allocateHuge(buf, normCapacity);
        }
    }

    private void allocateSmall(ThreadLocalCache cache, PooledByteBuf<T> buf, int reqCapacity, int sizeIdx) {
        if (cache.allocateCacheSmall(buf, sizeIdx)) {
            // was able to allocate out of the cache so move on
            // 缓存中取
            return;
        }

        final PoolSubpage<T> head = smallSubpagePools[sizeIdx];
        final boolean needsNormalAllocation;
        synchronized (head) {
            final PoolSubpage<T> subpage = head.next;
            // s == head ?
            needsNormalAllocation = subpage == head;
            if (!needsNormalAllocation) {
                // 分配subPage的handle位图信息
                long handle = subpage.allocate();
                assert handle >= 0;
                // 为buf分配内存
                subpage.chunk.initBufWithSubpage(buf, null, handle, reqCapacity, cache);
            }
        }

        if (needsNormalAllocation) {
            synchronized (this) {
                allocate(cache, buf, reqCapacity, sizeIdx);
            }
        }

        incSmallAllocation();
    }

    private void incSmallAllocation() {
        allocationsSmall.increment();
    }

    private void allocateHuge(PooledByteBuf<T> buf, int reqCapacity) {
        PoolChunk<T> chunk = newUnpooledChunk(reqCapacity);
        activeBytesHuge.add(chunk.chunkSize());
        // buf初始化
        buf.initUnpooled(chunk, reqCapacity);
        allocationsHuge.increment();
    }

    protected PoolChunk<T> newUnpooledChunk(int capacity) {
        if (directMemoryCacheAlignment == 0) {
            ByteBuffer memory = ByteBuffer.allocateDirect(capacity);
            return new PoolChunk(this, memory, memory, capacity);
        } else {
            final ByteBuffer base = ByteBuffer.allocateDirect(capacity + directMemoryCacheAlignment);
            final ByteBuffer memory = ByteBuffer.allocateDirect(capacity);
            return new PoolChunk(this, base, memory, capacity);
        }
    }

    private PooledByteBuf<T> newInstance(int maxCapacity) {
        PooledByteBuf buf = recycler.get();
        buf.reuse(maxCapacity);
        return buf;
    }

    private void allocateNormal(ThreadLocalCache cache, PooledByteBuf<T> buf, int reqCapacity, int sizeIdx) {
        // 缓存中取到可用的buf
        if (cache.allocateCacheNormal(this, buf, sizeIdx)) {
            return;
        }
        allocate(cache, buf, reqCapacity, sizeIdx);
    }

    private void allocate(ThreadLocalCache cache, PooledByteBuf<T> buf, int reqCapacity, int sizeIdx) {
        synchronized (this) {
            // 挨个从不同使用率的chunkList中获取normal大小的内存空间，获取不到则重新建立一个新chunk分配内存
            if (q050.allocate(buf, reqCapacity, cache, sizeIdx) ||
                    q025.allocate(buf, reqCapacity, cache, sizeIdx) ||
                    q000.allocate(buf, reqCapacity, cache, sizeIdx) ||
                    qInit.allocate(buf, reqCapacity, cache, sizeIdx) ||
                    q075.allocate(buf, reqCapacity, cache, sizeIdx)) {
                return;
            }
            // Add a new chunk.
            PoolChunk<T> poolChunk = newChunk(pageSize, nPSizes, pageShifts, chunkSize);
            poolChunk.allocate(buf, reqCapacity, cache, sizeIdx);
            qInit.add(poolChunk);
        }
    }

    private PoolChunk<T> newChunk(int pageSize, int nPSizes, int pageShifts, int chunkSize) {
        if (directMemoryCacheAlignment == 0) {
            ByteBuffer memory = ByteBuffer.allocateDirect(chunkSize);
            return new PoolChunk(this, memory, memory, pageSize, pageShifts, chunkSize, nPSizes);
        } else {
            ByteBuffer memory = ByteBuffer.allocateDirect(chunkSize);
            ByteBuffer base = ByteBuffer.allocateDirect(chunkSize);
            return new PoolChunk(this, base, memory, pageSize, pageShifts, chunkSize, nPSizes);
        }
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
            // 销毁chunk? 可用空间的回收
            destroyChunk = !chunk.parent.free(chunk, handle, normCapacity, nioBuffer);
        }
        if (destroyChunk) {
            // destroyChunk not need to be called while holding the synchronized lock.
            // 销毁chunk,创建新的PoolChunk
            destroyChunk(chunk);
        }

    }

    private void destroyChunk(PoolChunk chunk) {
        DirectBuffer base = (DirectBuffer) chunk.base;
        base.cleaner().clean();
    }


}
