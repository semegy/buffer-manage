import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;

public class SocketClient {

    public static Logger logger = Logger.getLogger(SocketClient.class.getName());

    @Test
    public static void main(String[] args) throws IOException {
        //打开选择器
        Selector selector = Selector.open();
        //打开通道
        SocketChannel socketChannel = SocketChannel.open();
        //配置非阻塞模型
        socketChannel.configureBlocking(false);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 80);
        //连接远程主机
        socketChannel.connect(address);
        //注册事件
        socketChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
        SocketChannel finalSocketChannel1 = socketChannel;
        Thread thread = new Thread(() -> {
            while (true) {
                Scanner scanner = new Scanner(System.in);
                String next = scanner.next();
                try {
                    if (next.equals("close")) {
                        finalSocketChannel1.close();
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
                    finalSocketChannel1.write(wrapper);
                    ByteBuffer wrapper2 = ByteBuffer.allocate(1024);
                    wrapper2.put("1234567890".getBytes(StandardCharsets.UTF_8));
                    wrapper2.flip();
                    finalSocketChannel1.write(wrapper2);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.start();
        //循环处理
        while (true) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                if (key.isConnectable()) {
                    //连接建立或者连接建立不成功
                    SocketChannel channel = (SocketChannel) key.channel();
                    boolean b = channel.finishConnect();
                    if (b) {
                        logger.info("连接成功");
                    }
                }
                if (key.isReadable()) {
                    try {
                        SocketChannel channel = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(500 * 1024 * 1024);
                        int read = channel.read(buffer);
                        logger.info(new String(buffer.array(), 0, buffer.position()));
                    } catch (IOException e) {
                        key.cancel();
                        key.channel().close();
                        logger.info("连接断开");
                    }
                }
                iter.remove();
            }
        }
    }
}
