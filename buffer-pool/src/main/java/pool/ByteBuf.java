package pool;

public interface ByteBuf<T> {
    void reused(int maxCapacity);
}
