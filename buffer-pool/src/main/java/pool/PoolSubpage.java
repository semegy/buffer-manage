package pool;

public class PoolSubpage<T> {

    private final int pageShifts /*13*/;
    private final int elemSize /*2048*/;
    /**
     * 当前子页所属chunk区
     */
    PoolChunk<T> chunk;

    /**
     * 定位前一个子页
     */
    PoolSubpage<T> prev;

    /**
     * 定位下一个子页
     */
    PoolSubpage<T> next;

    private int runOffset /*0*/;
    private int runSize /*8k*/;

    // bit位
    private long[] bitmap;

    PoolSubpage() {
        chunk = null;
        pageShifts = -1;
        runOffset = -1;
        elemSize = -1;
        runSize = -1;
        bitmap = null;
    }

    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int pageShifts, int runOffset, int runSize, int elemSize) {
        this.chunk = chunk;
        this.pageShifts = pageShifts;
        this.runOffset = runOffset;
        this.elemSize = elemSize;
        addToPool(head);
    }

    /**
     * 头插法
     *
     * @param head 池子中的头部页面，也就是要将当前对象添加到这个头部后面。
     */
    private void addToPool(PoolSubpage<T> head) {
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }
}
