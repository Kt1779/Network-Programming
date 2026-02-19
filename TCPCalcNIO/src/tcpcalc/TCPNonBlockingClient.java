package tcpcalc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

public class TCPNonBlockingClient {
    public static final String HOST = "localhost";

    public static void main(String[] args) throws Exception {
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter server port number (59686-59689) or press Enter to choose randomly: ");
        System.out.flush();
        String portLine = console.readLine();
        int port;
        if (portLine == null || portLine.trim().isEmpty()) {
            int[] p = {59686, 59687, 59688, 59689};
            port = p[new Random().nextInt(p.length)];
            System.out.println("Chosen port: " + port);
        } else {
            port = Integer.parseInt(portLine.trim());
        }
        System.out.print("TCP Client: Enter operand X (float): ");
        System.out.flush();
        String x = console.readLine().trim();
        System.out.print("TCP Client: Enter operand Y (float): ");
        System.out.flush();
        String y = console.readLine().trim();
        InetSocketAddress serverAddr = new InetSocketAddress(HOST, port);
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(serverAddr);
        Selector selector = Selector.open();
        channel.register(selector, SelectionKey.OP_CONNECT);
        StringBuilder readSb = new StringBuilder();
        boolean menuShown = false;
        while (true) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                if (key.isConnectable()) {
                    SocketChannel sc = (SocketChannel) key.channel();
                    if (sc.finishConnect()) {
                        ByteBuffer writeBuf = ByteBuffer.wrap((x + "\n" + y + "\n").getBytes(StandardCharsets.UTF_8));
                        while (writeBuf.hasRemaining()) sc.write(writeBuf);
                        key.interestOps(SelectionKey.OP_READ);
                    }
                } else if (key.isReadable()) {
                    SocketChannel sc = (SocketChannel) key.channel();
                    ByteBuffer rb = ByteBuffer.allocate(512);
                    int r = sc.read(rb);
                    if (r == -1) {
                        sc.close();
                        selector.close();
                        console.close();
                        return;
                    }
                    rb.flip();
                    readSb.append(StandardCharsets.UTF_8.decode(rb).toString());
                    int idx;
                    while ((idx = readSb.indexOf("\n")) >= 0) {
                        String line = readSb.substring(0, idx).trim();
                        readSb.delete(0, idx + 1);
                        if (!menuShown) {
                            System.out.println("Server: " + line);
                            menuShown = true;
                        } else {
                            if (!line.isEmpty()) {
                                System.out.println("Server response: " + line);
                                sc.close();
                                selector.close();
                                console.close();
                                return;
                            }
                        }
                    }
                }
            }
            if (menuShown) break;
        }
        outer:
        while (true) {
            System.out.print("Enter choice (1/2/3/4 or + - * /): ");
            System.out.flush();
            String choice = console.readLine().trim();
            ByteBuffer cb = ByteBuffer.wrap((choice + "\n").getBytes(StandardCharsets.UTF_8));
            while (cb.hasRemaining()) channel.write(cb);
            while (true) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                boolean needRetry = false;
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer rb = ByteBuffer.allocate(512);
                        int r = sc.read(rb);
                        if (r == -1) {
                            sc.close();
                            selector.close();
                            console.close();
                            return;
                        }
                        rb.flip();
                        readSb.append(StandardCharsets.UTF_8.decode(rb).toString());
                        int idx;
                        while ((idx = readSb.indexOf("\n")) >= 0) {
                            String line = readSb.substring(0, idx).trim();
                            readSb.delete(0, idx + 1);
                            if (line.isEmpty()) continue;
                            System.out.println("Server response: " + line);
                            if (line.startsWith("Wrong choice!")) {
                                needRetry = true;
                                break;
                            } else {
                                sc.close();
                                selector.close();
                                console.close();
                                return;
                            }
                        }
                        if (needRetry) break;
                    }
                }
                if (needRetry) break;
            }
        }
    }
}
