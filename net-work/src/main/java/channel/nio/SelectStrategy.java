package channel.nio;

public interface SelectStrategy {

    /**
     * Indicates a blocking select should follow.
     */
    int SELECT = 0;
    /**
     * Indicates the IO loop should be retried, no blocking select to follow directly.
     */
    int CONTINUE = -2;
    /**
     * Indicates the IO loop to poll for new events without blocking.
     */
    int BUSY_WAIT = -3;
}
