package pool;

import pool.recycle.ThreadLocalCache;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import static pool.LongPriorityQueue.newRunsAvailQueueArray;

final public class PoolChunk<T> implements Chunk {


    private static final int SUBPAGE_BIT_LENGTH = 1;

    private static final int BITMAP_IDX_BIT_LENGTH = 32;

    static final int IS_SUBPAGE_SHIFT = BITMAP_IDX_BIT_LENGTH;

    private static final int INUSED_BIT_LENGTH = 1;
    private static final int IS_USED_SHIFT = SUBPAGE_BIT_LENGTH + IS_SUBPAGE_SHIFT;

    static final int SIZE_SHIFT = INUSED_BIT_LENGTH + IS_USED_SHIFT;
    static final int SIZE_BIT_LENGTH = 15;

    static final int RUN_OFFSET_SHIFT = SIZE_BIT_LENGTH + SIZE_SHIFT;


    final T memory;

    private final PoolSubpage<T>[] subpages;

    private final int pageSize;

    private final int pageShifts;

    private final int chunkSize;
    public PoolArena arena;

    PoolChunkList<T> parent;

    int freeBytes;

    // todo 默认chunk 分区 16M， 后面拓展改造
    private static int DEFAULT_MAX_CHUNK_SIZE = 1024 * 1024 * 16;
    // todo 默认chunk 分区 8k， 后面拓展改造
    private static int DEFAULT_PAGE_SIZE = 1024 * 8;
    // 页位偏移量
    private static int PAGE_SHIFTS = 13;


    private static int IN_USED_BIT_LENGTH = 32;

    private static int IS_USAGE = 1;

    private static int IS_SUBPAGE = 1;

    private final int nPSizes = 40;

    /**
     * manage all avail runs
     */
    private final LongPriorityQueue[] runsAvail;

    /**
     * store the first page and last page of each avail run
     */
    private final LongLongHashMap runsAvailMap;

    public PoolChunk<T> next;

    PoolChunk<T> prev;

    // Use as cache for ByteBuffer created from the memory. These are just duplicates and so are only a container
    // around the memory itself. These are often needed for operations within the Pooled*ByteBuf and so
    // may produce extra GC, which can be greatly reduced by caching the duplicates.
    //
    // This may be null if the PoolChunk is unpooled as pooling the ByteBuffer instances does not make any sense here.
    private final Deque<ByteBuffer> cachedNioBuffers;

    public PoolChunk(PoolArena arena, T memory) {
        this.arena = arena;
        this.memory = memory;
        this.pageSize = DEFAULT_PAGE_SIZE;
        this.pageShifts = PAGE_SHIFTS;
        this.chunkSize = DEFAULT_MAX_CHUNK_SIZE;
        this.freeBytes = DEFAULT_MAX_CHUNK_SIZE;
        // chunk区分成多少页，此算法相当于chunkSize/pageSize, 用移位的方式更加高效
        this.subpages = new PoolSubpage[DEFAULT_MAX_CHUNK_SIZE >> PAGE_SHIFTS];
        int pages = DEFAULT_MAX_CHUNK_SIZE >> PAGE_SHIFTS;
        runsAvail = newRunsAvailQueueArray(pages);
        runsAvailMap = new LongLongHashMap(-1);
        long initHandle = (long) pages << (IN_USED_BIT_LENGTH + IS_USAGE + IS_SUBPAGE);
        insertAvailRun(0, DEFAULT_MAX_CHUNK_SIZE >> PAGE_SHIFTS, initHandle);
        cachedNioBuffers = new ArrayDeque<ByteBuffer>(8);
    }

    private void insertAvailRun(int runOffset, int pages, long handle) {
        int pageIdxFloor = arena.pages2pageIdxFloor(pages);
        // 获取queue
        LongPriorityQueue queue = runsAvail[pageIdxFloor];
        queue.offer(handle);
        //insert first page of run
        // 记录首页与末叶的句柄
        insertAvailRun0(runOffset, handle);
        if (pages > 1) {
            //insert last page of run
            insertAvailRun0(lastPage(runOffset, pages), handle);
        }
    }

    private static int lastPage(int runOffset, int pages) {
        return runOffset + pages - 1;
    }


    private void insertAvailRun0(int runOffset, long handle) {
        long pre = runsAvailMap.put(runOffset, handle);
        assert pre == -1;
    }

    private static ByteBuffer allocateDirect(int capacity) {
        // todo Unsafe.allocateDirect(capacity);
        return ByteBuffer.allocateDirect(capacity);
    }

    private void allocateNormal(PooledByteBuf<ByteBuffer> buf, int reqCapacity, int sizeIdx) {

        // todo 从队列中获取去chunk

        // add a new chunk
        PoolChunk<ByteBuffer> chunk = new PoolChunk<>(null, allocateDirect(chunkSize));
        chunk.allocate(buf, reqCapacity, sizeIdx);
    }

    public boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int sizeIdx) {
        // todo subpage 子页分配

        // 超出子页大小的 normal分配机制
        // runSize must be multiple of pageSize
        // runSize分配机制的索引位计算
        int runSize = arena.sizeIdx2size(sizeIdx);
        long handle = allocateNormal(runSize);
        if (handle == -1) {
            return false;
        }
        // todo handle规范检查
//        assert !isSubpage(handle);
        ByteBuffer nioBuffer = cachedNioBuffers != null ? cachedNioBuffers.pollLast() : null;
        initBuf(buf, nioBuffer, handle, reqCapacity, null);
        return true;
    }

    public void initBuf(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity, ThreadLocalCache threadCache) {
        int maxLength = runSize(pageShifts, handle);
        buf.init(this, nioBuffer, handle, runOffset(handle) << pageShifts,
                reqCapacity, maxLength);
    }

    private long allocateNormal(int runSize) {
        // 根据分配的实际大小，计算出页数
        int pages = runSize >> pageShifts;
        // 获取页索引
        int pageIdx = arena.pages2pageIdx(pages);
        synchronized (runsAvail) {
            //find first queue which has at least one big enough run
            long handle = freeBytes == chunkSize ? arena.nPSizes - 1 : -1;
            for (int i = pageIdx; i < arena.nPSizes; i++) {
                LongPriorityQueue queue = runsAvail[i];
                if (queue != null && !queue.isEmpty()) {
                    handle = queue.poll();
                    assert handle != LongPriorityQueue.NO_VALUE && !isUsed(handle) : "invalid handle: " + handle;
                    // 移除旧queue
                    removeAvailRun(queue, handle);
                    if (handle != -1) {
                        // 拆分run
                        handle = splitLargeRun(handle, pages);
                    }
                    // 偏移量
                    int pinnedSize = runSize(pageShifts, handle);
                    // 减少空闲
                    freeBytes -= pinnedSize;
                    return handle;
                }
            }
            return -1;
        }
    }

    private long splitLargeRun(long handle, int pages) {
        assert pages > 0;
        int totalPages = runPages(handle);
        assert pages <= totalPages;
        // 剩余空页
        int remPages = totalPages - pages;
        if (remPages > 0) {
            // 当前前偏移页
            int runOffset = runOffset(handle);
            // 空闲run的偏移页
            int availOffset = runOffset + pages;
            // 剩余可用run插入队列
            long availRun = toRunHandle(availOffset, remPages, 0);
            // 更新 availRun 与 availRunMap
            insertAvailRun(availOffset, remPages, handle);
            // 使用信息保存至高32位 runOffSet:15, 本次占用页数:15， 1 占用标识:1; 0：分配子页标识:1位
            return toRunHandle(runOffset, pages, 1);
        }
        //mark it as used 刚好被占用，修改占用标记即可
        handle |= 1L << IS_USED_SHIFT;
        return handle;
    }

    private long toRunHandle(int runOffset, int remPages, int isUsed) {
        return (long) runOffset << RUN_OFFSET_SHIFT | remPages << SIZE_SHIFT | isUsed;
    }

    private int runOffset(long handle) {
        return (int) handle >> RUN_OFFSET_SHIFT & 0x7fff;
    }

    private int runPages(long handle) {
        return (int) (handle >> SIZE_SHIFT & 0x7fff);
    }

    private int runSize(int pageShifts, long handle) {
        return 0;
    }

    private void removeAvailRun(LongPriorityQueue queue, long handle) {
        queue.remove(handle);
        // 页偏移量
        int runOffset = runOffset(handle);
        // 页数
        int pages = runPages(handle);
        //remove first page of run
        runsAvailMap.remove(runOffset);
        if (pages > 1) {
            //remove last page of run
            runsAvailMap.remove(lastPage(runOffset, pages));
        }
    }

    private long runFirstBestFit(int pageIdx) {
        if (freeBytes == chunkSize) {
            return arena.nPSizes - 1;
        } else {
            for (int i = pageIdx; i < arena.nPSizes; i++) {
                LongPriorityQueue queue = runsAvail[i];
                if (queue != null && !queue.isEmpty()) {
                    long handle = queue.poll();
                    assert handle != LongPriorityQueue.NO_VALUE && !isUsed(handle) : "invalid handle: " + handle;
                    return handle;
                }
            }
        }
        return -1;
    }

    private boolean isUsed(long handle) {
        return (handle >> IS_USED_SHIFT & 1) == 1L;
    }

    @Override
    public int chunkSize() {
        return this.chunkSize;
    }

    @Override
    public int freeChunkSize() {
        return this.freeBytes;
    }

    @Override
    public int usage() {
        return chunkSize - freeBytes;
    }


    public void incrementPinnedMemory(int maxLength) {
//        assert delta > 0;
//        pinnedBytes.add(delta);
    }

    public void free(long handle, int normCapacity, ByteBuffer nioBuffer) {
        int runSize = runSize(pageShifts, handle);
//        if (isSubpage(handle)) {
//            int sizeIdx = arena.size2SizeIdx(normCapacity);
//            PoolSubpage<T> head = arena.findSubpagePoolHead(sizeIdx);
//
//            int sIdx = runOffset(handle);
//            PoolSubpage<T> subpage = subpages[sIdx];
//            assert subpage != null && subpage.doNotDestroy;
//
//            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
//            // This is need as we may add it back and so alter the linked-list structure.
//            synchronized (head) {
//                if (subpage.free(head, bitmapIdx(handle))) {
//                    //the subpage is still used, do not free it
//                    return;
//                }
//                assert !subpage.doNotDestroy;
//                // Null out slot in the array as it was freed and we should not use it anymore.
//                subpages[sIdx] = null;
//            }
//        }

        //start free run
        synchronized (runsAvail) {
            // collapse continuous runs, successfully collapsed runs
            // will be removed from runsAvail and runsAvailMap
            long finalRun = collapseRuns(handle);

            //set run as not used
            finalRun &= ~(1L << IS_USED_SHIFT);
            //if it is a subpage, set it to run
            finalRun &= ~(1L << IS_SUBPAGE_SHIFT);

            insertAvailRun(runOffset(finalRun), runPages(finalRun), finalRun);
            freeBytes += runSize;
        }

        if (nioBuffer != null && cachedNioBuffers != null &&
                cachedNioBuffers.size() < PooledBufferAllocate.DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK) {
            cachedNioBuffers.offer(nioBuffer);
        }
    }

    private long collapseRuns(long handle) {
        return collapseNext(collapsePast(handle));
    }

    private long collapsePast(long handle) {
        for (; ; ) {
            int runOffset = runOffset(handle);
            int runPages = runPages(handle);

            long pastRun = getAvailRunByOffset(runOffset - 1);
            if (pastRun == -1) {
                return handle;
            }

            int pastOffset = runOffset(pastRun);
            int pastPages = runPages(pastRun);

            //is continuous
            if (pastRun != handle && pastOffset + pastPages == runOffset) {
                //remove past run
                removeAvailRun(pastRun);
                handle = toRunHandle(pastOffset, pastPages + runPages, 0);
            } else {
                return handle;
            }
        }
    }

    private void removeAvailRun(long handle) {
        int pageIdxFloor = arena.pages2pageIdxFloor(runPages(handle));
        LongPriorityQueue queue = runsAvail[pageIdxFloor];
        removeAvailRun(queue, handle);
    }

    private long collapseNext(long handle) {
        for (; ; ) {
            int runOffset = runOffset(handle);
            int runPages = runPages(handle);

            long nextRun = getAvailRunByOffset(runOffset + runPages);
            if (nextRun == -1) {
                return handle;
            }

            int nextOffset = runOffset(nextRun);
            int nextPages = runPages(nextRun);

            //is continuous
            if (nextRun != handle && runOffset + runPages == nextOffset) {
                //remove next run
                removeAvailRun(nextRun);
                handle = toRunHandle(runOffset, runPages + nextPages, 0);
            } else {
                return handle;
            }
        }
    }

    private long getAvailRunByOffset(int runOffset) {
        return runsAvailMap.get(runOffset);
    }
}
