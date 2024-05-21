package channel;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class ChannelWrapper {

    SelectionKey selectionKey;

    Selector selector;

    ServerSocketChannel channel;

    public ChannelWrapper(SelectionKey key, ServerSocketChannel channel, Selector selector) {
        this.selectionKey = key;
        this.channel = channel;
        this.selector = selector;
    }

    public static ChannelWrapper wrapper(SelectionKey key, ServerSocketChannel socketChannel, Selector selector) {
        return new ChannelWrapper(key, socketChannel, selector);
    }

    public static ChannelWrapper wrapper(SelectionKey key, Selector selector) {
        return new ChannelWrapper(key, null, selector);
    }
}
