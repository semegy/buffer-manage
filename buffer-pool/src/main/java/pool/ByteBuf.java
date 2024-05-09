package pool;

public interface ByteBuf<T> {
    void reused(int maxCapacity);

    /**
     * 释放资源
     */
    void deallocate();
}
