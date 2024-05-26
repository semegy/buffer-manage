package buffer.pool;

import buffer.recycle.ThreadLocalCache;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.LongAdder;

import static buffer.pool.LongPriorityQueue.newRunsAvailQueueArray;

final public class PoolChunk<T> implements Chunk {


    private static final int SUBPAGE_BIT_LENGTH = 1;

    private static final int BITMAP_IDX_BIT_LENGTH = 32;

    static final int IS_SUBPAGE_SHIFT = BITMAP_IDX_BIT_LENGTH;

    private static final int INUSED_BIT_LENGTH = 1;
    static final int IS_USED_SHIFT = SUBPAGE_BIT_LENGTH + IS_SUBPAGE_SHIFT;

    static final int SIZE_SHIFT = INUSED_BIT_LENGTH + IS_USED_SHIFT;
    static final int SIZE_BIT_LENGTH = 15;

    static final int RUN_OFFSET_SHIFT = SIZE_BIT_LENGTH + SIZE_SHIFT;


    final T memory;

    private final PoolSubpage<T>[] subpages;

    private final int pageSize;

    private final int pageShifts;

    private final int chunkSize;
    public final boolean pooled;
    public PoolArena arena;
    public final T base;

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
     * Accounting of pinned memory – memory that is currently in use by ByteBuf instances.
     */
    private final LongAdder pinnedBytes = new LongAdder();

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

    private final Deque<ByteBuffer> cachedNioBuffers;

    PoolChunk(PoolArena<T> arena, T base, T memory, int size) {
        pooled = false;
        this.arena = arena;
        this.base = base;
        this.memory = memory;
        pageSize = 0;
        pageShifts = 0;
        runsAvailMap = null;
        runsAvail = null;
        subpages = null;
        chunkSize = size;
        cachedNioBuffers = null;
    }

    @SuppressWarnings("unchecked")
    public PoolChunk(PoolArena<T> arena, T base, T memory, int pageSize, int pageShifts, int chunkSize, int maxPageIdx) {
        pooled = true;
        this.arena = arena;
        this.base = base;
        this.memory = memory;
        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.chunkSize = chunkSize;
        freeBytes = chunkSize;
        runsAvail = newRunsAvailQueueArray(maxPageIdx);
        runsAvailMap = new LongLongHashMap(-1);
        subpages = new PoolSubpage[chunkSize >> pageShifts];

        //insert initial run, offset = 0, pages = chunkSize / pageSize
        int pages = chunkSize >> pageShifts;
        // pageSize 信息保存至高位34-49 handle 15 位 pageOffSet + 15 位 pageSize 15 位 + 1 位 isUsed + 1 位 isSubpage + 32 位 bitmapIdx
        long initHandle = (long) pages << SIZE_SHIFT;
        insertAvailRun(0, pages, initHandle);

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

    public boolean allocate(PooledByteBuf<T> buf, int reqCapacity, ThreadLocalCache cache, int sizeIdx) {
        final long handle;
        if (sizeIdx <= arena.smallMaxSizeIdx) {
            // subpage分配
            handle = allocateSubpage(sizeIdx);
        } else {
            // 超出子页大小的 normal分配机制
            // runSize must be multiple of pageSize
            int runSize = arena.sizeIdx2size(sizeIdx);
            handle = allocateNormal(runSize);
            assert !isSubpage(handle);
        }
        if (handle < 0) {
            return false;
        }
        ByteBuffer nioBuffer = cachedNioBuffers != null ? cachedNioBuffers.pollLast() : null;
        initBuf(buf, nioBuffer, handle, reqCapacity, cache);
        return true;
    }

    /**
     * Create / initialize a new PoolSubpage of normCapacity. Any PoolSubpage created / initialized here is added to
     * subpage pool in the PoolArena that owns this PoolChunk
     *
     * @return index in memoryMap
     */
    private long allocateSubpage(int sizeIdx) {
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.
        PoolSubpage<T> head = arena.findSubpagePoolHead(sizeIdx);
        synchronized (head) {
            //allocate a new run
            // 规格化计算出runSize
            int runSize = calculateRunSize(sizeIdx);
            //runSize must be multiples of pageSize
            long runHandle = allocateRun(runSize);
            if (runHandle < 0) {
                return -1;
            }
            int runOffset = runOffset(runHandle);
            assert subpages[runOffset] == null;
            int elemSize = arena.sizeIdx2size(sizeIdx);

            PoolSubpage<T> subpage = new PoolSubpage<T>(head, this, pageShifts, runOffset,
                    runSize(pageShifts, runHandle), elemSize);

            subpages[runOffset] = subpage;
            return subpage.allocate();
        }
    }


    private int calculateRunSize(int sizeIdx) {
        // 最大512
        int maxElements = 1 << 13 - 4;
        int runSize = 0;
        int nElements;

        // 存储块大小
        final int elemSize = arena.sizeIdx2size(sizeIdx);

        //find lowest common multiple of pageSize and elemSize
        do {
            runSize += pageSize;
            nElements = runSize / elemSize;
        } while (nElements < maxElements && runSize != nElements * elemSize);

        while (nElements > maxElements) {
            runSize -= pageSize;
            nElements = runSize / elemSize;
        }

        assert nElements > 0;
        assert runSize <= chunkSize;
        assert runSize >= elemSize;

        return runSize;
    }

    public void initBuf(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity, ThreadLocalCache cache) {
        if (isSubpage(handle)) {
            initBufWithSubpage(buf, nioBuffer, handle, reqCapacity, cache);
        } else {
            int maxLength = runSize(pageShifts, handle);
            buf.init(this, nioBuffer, handle, runOffset(handle) << pageShifts,
                    reqCapacity, maxLength, arena.parent.threadCache());
        }
    }

    public static boolean isSubpage(long handle) {
        return (handle >> IS_SUBPAGE_SHIFT & 1) == 1L;
    }

    static int bitmapIdx(long handle) {
        return (int) handle;
    }

    private long allocateNormal(int runSize) {
        synchronized (runsAvail) {
            return allocateRun(runSize);
        }
    }

    private long allocateRun(int runSize) {
        // 根据分配的实际大小，计算出页数
        int pages = runSize >> pageShifts;
        // 获取页索引
        int pageIdx = arena.pages2pageIdx(pages);
        if (freeBytes == chunkSize) {
            LongPriorityQueue queue = runsAvail[nPSizes - 1];
            return handle(queue, pages);
        } else {
            for (int i = pageIdx; i < arena.nPSizes; i++) {
                LongPriorityQueue queue = runsAvail[i];
                if (queue != null && !queue.isEmpty()) {
                    return handle(queue, pages);
                }
            }
        }
        return -1;
    }

    private long handle(LongPriorityQueue queue, int pages) {
        long handle = queue.poll();
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
            insertAvailRun(availOffset, remPages, availRun);
            // 使用信息保存至高32位 runOffSet:15, 本次占用页数:15， 1 占用标识:1; 0：分配子页标识:1位
            return toRunHandle(runOffset, pages, 1);
        }
        //mark it as used 刚好被占用，修改占用标记即可
        handle |= 1L << IS_USED_SHIFT;
        return handle;
    }

    private long toRunHandle(int runOffset, int runPages, int isUsed) {
        return (long) runOffset << RUN_OFFSET_SHIFT
                | (long) runPages << SIZE_SHIFT
                | (long) isUsed << IS_USED_SHIFT;
    }

    private int runOffset(long handle) {
        return (int) (handle >> RUN_OFFSET_SHIFT);
    }

    private int runPages(long handle) {
        return (int) (handle >> SIZE_SHIFT & 0x7fff);
    }

    private int runSize(int pageShifts, long handle) {
        return runPages(handle) << pageShifts;
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


    public void incrementPinnedMemory(int delta) {
        assert delta > 0;
        pinnedBytes.add(delta);
    }

    public void free(long handle, int normCapacity, ByteBuffer nioBuffer) {
        int runSize = runSize(pageShifts, handle);
        if (isSubpage(handle)) {
            int sizeIdx = arena.size2SizeIdx(normCapacity);
            PoolSubpage<T> head = arena.findSubpagePoolHead(sizeIdx);

            int sIdx = runOffset(handle);
            PoolSubpage<T> subpage = subpages[sIdx];
//            assert subpage != null && subpage.doNotDestroy;

            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
            // This is need as we may add it back and so alter the linked-list structure.
            synchronized (head) {
                if (subpage.free(head, bitmapIdx(handle))) {
                    //the subpage is still used, do not free it
                    return;
                }
//                assert !subpage.doNotDestroy;
                // Null out slot in the array as it was freed and we should not use it anymore.
                subpages[sIdx] = null;
            }
        }

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

    public void decrementPinnedMemory(int delta) {
        assert delta > 0;
        pinnedBytes.add(-delta);
    }

    public void initBufWithSubpage(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity, ThreadLocalCache cache) {
        // subPages索引页
        int runOffset = runOffset(handle);
        // 所在的存储块索引 handle低32位
        int bitmapIdx = bitmapIdx(handle);

        // subPage获取runOffset
        PoolSubpage<T> s = subpages[runOffset];
//        assert s.doNotDestroy;
//        assert reqCapacity <= s.elemSize : reqCapacity + "<=" + s.elemSize;

        // 地址偏移量 = 索引页 * 8K + 当前页的bitMap索引位*内存块的偏移地址
        int offset = (runOffset << pageShifts) + bitmapIdx * s.elemSize;
        buf.init(this, nioBuffer, handle, offset, reqCapacity, s.elemSize, cache);
    }
}
