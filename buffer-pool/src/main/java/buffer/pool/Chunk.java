package buffer.pool;

public interface Chunk {

    /**
     * 返回chunk的占用bytes的字节
     */
    int chunkSize();

    /**
     * chunk空闲字节
     */
    int freeChunkSize();

    /**
     * chunk使用字节
     */
    int usage();
}
