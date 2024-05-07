package pool;

/**
 * 给定一个buffer分配置器
 */
public interface BufferAllocate {

    static final int DEFAULT_MAX_CAPACITY = Integer.MAX_VALUE;

    public ByteBuf ioBuffer(int initialCapacity);

    /**
     * Creates a new handle.  The handle provides the actual operations and keeps the internal information which is
     * required for predicting an optimal buffer capacity.
     */
    Handle newHandle();


    @Deprecated
    public interface Handle {
        public ByteBuf allocate();

        int guess();

        /**
         * Increment the number of messages that have been read for the current read loop.
         *
         * @param numMessages The amount to increment by.
         *                    增加已读消息数
         */
        void incMessagesRead(int numMessages);

        /**
         * Set the bytes that have been read for the last read operation.
         * This may be used to increment the number of bytes that have been read.
         *
         * @param bytes The number of bytes from the previous read operation. This may be negative if an read error
         *              occurs. If a negative value is seen it is expected to be return on the next call to
         *              {@link #lastBytesRead()}. A negative value will signal a termination condition enforced externally
         *              to this class and is not required to be enforced in {@link #continueReading()}.
         *              记录上次读取字节数，AdaptiveRecvByteBufAllocator根据该值 自适应调整下次分配缓冲区大小
         */
        void lastBytesRead(int bytes);

        /**
         * Get the amount of bytes for the previous read operation.
         *
         * @return The amount of bytes for the previous read operation.
         * 获取上次读取字节数
         */
        int lastBytesRead();

        /**
         * Set how many bytes the read operation will (or did) attempt to read.
         *
         * @param bytes How many bytes the read operation will (or did) attempt to read.
         *              尝试读取字节数
         */
        void attemptedBytesRead(int bytes);

        /**
         * Get how many bytes the read operation will (or did) attempt to read.
         *
         * @return How many bytes the read operation will (or did) attempt to read.
         * 获取尝试读取字节数
         */
        int attemptedBytesRead();

        /**
         * Determine if the current read loop should continue.
         *
         * @return {@code true} if the read loop should continue reading. {@code false} if the read loop is complete.
         * 是否继续读
         */
        boolean continueReading();

        /**
         * The read has completed.
         * 完成读取
         */
        void readComplete();
    }

}
