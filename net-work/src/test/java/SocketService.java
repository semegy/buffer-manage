import nio.EventHandlerContext;
import nio.HandlerInvoker;
import nio.NioReactorEndpoint;
import nio.SimpleEventHandler;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;

public class SocketService {
    public static boolean done = false;

    public static void main(String[] args) throws IOException, InterruptedException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        EventHandlerContext context = new EventHandlerContext(new SimpleEventHandler(), null, null, new HandlerInvoker());
        NioReactorEndpoint nioReactorEndpoint = new NioReactorEndpoint(serverSocketChannel, context);
        nioReactorEndpoint.start();
        synchronized (serverSocketChannel) {
            // 未结束
            while (!done) {
                serverSocketChannel.wait();
            }
        }
    }
}
