import channel.ChannelContainer;
import channel.ChannelContext;
import channel.message.HandlerInitializer;
import channel.message.StringDecoder;
import channel.message.StringEncoder;
import org.junit.Test;

import java.net.InetSocketAddress;

public class SocketService {
    public static boolean done = false;

    @Test
    public void test() throws InterruptedException {
        HandlerInitializer initializer = new HandlerInitializer() {
            @Override
            public void initHandler(ChannelContext context) {
                context.addHandler(new StringDecoder(Integer.MAX_VALUE, 0, 2, 0, 2))
                        .addHandler(new StringEncoder(Integer.MAX_VALUE, 0, 2, 0, 2));
            }
        };
        InetSocketAddress address = new InetSocketAddress("localhost", 80);
        ChannelContainer context = new ChannelContainer(initializer, 16, address);
        context.start();
        synchronized (context) {
            // 未结束
            while (!done) {
                context.wait();
            }
        }
    }
}
