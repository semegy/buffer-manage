package pool;

import java.nio.ByteBuffer;

public class PoolArena<T> extends SizeClasses{
    final int nPSizes;
    PoolSubpage<T>[] smallSubpagePools;
    public PoolArena(int pageSize, int pageShifts, int chunkSize, int cacheAlignment) {
        super(pageSize, pageShifts, chunkSize, cacheAlignment);
        this.nPSizes = 40;
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
        for (int i = 0; i < nSubpages; i ++) {
            poolSubpages[i] = newSubpagePoolHead();
        }
        return poolSubpages;
    }

    public PooledByteBuf<T> allocate(PoolThreadCache cache, int reqCapacity, int maxCapacity) {
        // 先从缓存池中获取到合适大小的ByteBuf
        PooledByteBuf<T> buf = newByteBuf(maxCapacity);
        // 从cache中找到合适的分区分配内存
        // 原理 根据请求的空间大找到合适sizeIdx （寻址索引）
        final int sizeIdx = size2SizeIdx(reqCapacity);
        allocateNormal(buf, reqCapacity, sizeIdx, null);
        return buf;
    }
    private PooledByteBuf<T> newByteBuf(int maxCapacity) {
        PooledByteBuf buf = new PooledByteBuf();
        return buf;
    }

    private Boolean allocateNormal(PooledByteBuf<T> buf, int reqCapacity, int sizeIdx, PoolThreadCache threadCache) {
        // Add a new chunk.
        ByteBuffer memory = ByteBuffer.allocateDirect(reqCapacity);
        PoolChunk poolChunk = newChunk(this, memory);
        return poolChunk.allocate(buf, reqCapacity, sizeIdx, threadCache);
    }

    PoolChunk<ByteBuffer> newChunk(PoolArena arena, ByteBuffer memory) {
        return new PoolChunk<ByteBuffer>(this, memory);
    }
}
