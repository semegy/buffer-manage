import channel.ChannelContainer;
import channel.ChannelContext;
import channel.message.HandlerInitializer;
import channel.message.StringDecoder;
import channel.message.StringEncoder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Scanner;

public class StringClient {

    public static void main(String[] args) throws Exception {

        HandlerInitializer initializer = new HandlerInitializer() {
            @Override
            public void initHandler(ChannelContext context) {
                context.addHandler(new StringDecoder(Integer.MAX_VALUE, 0, 2, 0, 2))
                        .addHandler(new StringEncoder(Integer.MAX_VALUE, 0, 2, 0, 2));
            }
        };
        InetSocketAddress address = new InetSocketAddress(80);
        ChannelContainer container = new ChannelContainer(initializer, 16);
        ChannelContext context = container.connect(address);
        Thread thread = new Thread(() -> {
            while (true) {
                Scanner scanner = new Scanner(System.in);
                String next = scanner.next();
                try {
                    if (next.equals("close")) {
                        container.channel.close();
                        break;
                    }
                    context.write("1234567");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.start();
        synchronized (context) {
            context.wait();
        }
    }
}
