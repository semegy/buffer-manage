import channel.ChannelContainer;
import channel.ChannelContext;
import channel.message.HandlerInitializer;
import channel.message.StringDecoder;
import channel.message.StringEncoder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class HttpClientTest {

    public static void main(String[] args) throws Exception {

        HandlerInitializer initializer = new HandlerInitializer() {
            @Override
            public void initHandler(ChannelContext context) {
                context.addHandler(new StringDecoder(Integer.MAX_VALUE, 0, 2, 0, 2))
                        .addHandler(new StringEncoder(Integer.MAX_VALUE, 0, 2, 0, 2));
            }
        };
        InetSocketAddress address = new InetSocketAddress(80);
        ChannelContainer context = new ChannelContainer(initializer, 1, null, ServerSocket.class);
        context.connect(address, initializer);
        Thread thread = new Thread(() -> {
            while (true) {
                Scanner scanner = new Scanner(System.in);
                String next = scanner.next();
                try {
                    if (next.equals("close")) {
                        context.channel.close();
                        break;
                    }
                    ByteBuffer wrapper = ByteBuffer.allocate(1024);
                    wrapper.put(new byte[]{0, 20}); // 假设长度字段表示帧长度
                    wrapper.put("1234567890".getBytes(StandardCharsets.UTF_8));
                    wrapper.put("1234567890".getBytes(StandardCharsets.UTF_8));
                    wrapper.put(new byte[]{0, 20}); // 假设长度字段表示帧长度
                    wrapper.put("1234567890".getBytes(StandardCharsets.UTF_8));
                    wrapper.put("1234567890".getBytes(StandardCharsets.UTF_8));
                    wrapper.put(new byte[]{0, 20}); // 假设长度字段表示帧长度
                    wrapper.put("1234567890".getBytes(StandardCharsets.UTF_8));
                    wrapper.put("1234567890".getBytes(StandardCharsets.UTF_8));
                    wrapper.put(new byte[]{0, 20}); // 假设长度字段表示帧长度
                    wrapper.put("1234567890".getBytes(StandardCharsets.UTF_8));
                    wrapper.flip();
                    ((SocketChannel) context.channel).write(wrapper);
                    ByteBuffer wrapper2 = ByteBuffer.allocate(1024);
                    wrapper2.put("1234567890".getBytes(StandardCharsets.UTF_8));
                    wrapper2.flip();
                    ((SocketChannel) context.channel).write(wrapper2);
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
