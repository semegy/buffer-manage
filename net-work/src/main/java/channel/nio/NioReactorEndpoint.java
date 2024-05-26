package channel.nio;

import channel.ChannelContext;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

public class NioReactorEndpoint extends ReactorEndpoint {
    Selector selector;

    SelectableChannel socketChannel;

    private final AtomicLong nextWakeupNanos = new AtomicLong(System.nanoTime());

    public NioReactorEndpoint() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        int selectCnt = 0;
        while (true) {
            // 是否有事件
            int select = 0;
            try {
                select = selector.select(5000);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            switch (select) {
                case SelectStrategy.CONTINUE:
                    continue;
                case SelectStrategy.BUSY_WAIT:
                    // 有事件，轮询IO事件
                    selectCnt++;
                    return;
                case SelectStrategy.SELECT:
                    // 下次select时间
                    try {
                        select = select(nextWakeupNanos.get());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    nextWakeupNanos.set(System.nanoTime());
                    selectCnt = select == 0 ? selectCnt++ : 0;
                    break;
                default:
            }
            if (select > 0) {
                processSelectedKeys();
            }
            if (selectCnt > 10000) {
                try {
                    this.selector = Selector.open();
                    socketChannel.register(this.selector, OP_ACCEPT);
                } catch (IOException e) {
                }
            }
        }
    }

    private void processSelectedKeys() {
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            if (key.isValid()) {
                ChannelContext ctx = (ChannelContext) key.attachment();
                if (key.isAcceptable()) {
                    ctx.invokerAccept();
                } else if (key.isReadable()) {
                    ctx.invokerRead();
                } else if (key.isWritable()) {
                    ctx.invokerWrite();
                } else if (key.isConnectable()) {
                    ctx.invokerCompleteConnect();
                }
            } else {
                try {
                    socketChannel.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            selectionKeys.clear();
        }
    }

    private int select(long deadlineNanos) throws IOException {
        long timeoutMillis = System.nanoTime() - deadlineNanos;
        // 5秒钟一次 执行非阻塞selectNow, 超过时间执行阻塞式select
        return timeoutMillis >= 5000000000L ? selector.selectNow() : selector.select(timeoutMillis);
    }

    public void register(SelectableChannel channel, int ops, ChannelContext channelContext) {
        try {
            channel.register(this.selector, ops, channelContext);
        } catch (ClosedChannelException e) {
        }
    }

    public Selector selector() {
        return selector;
    }
}
