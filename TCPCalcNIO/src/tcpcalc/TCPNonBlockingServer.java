package tcpcalc;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

public class TCPNonBlockingServer {
    public static void main(String[] args) throws Exception {
        int[] ports = {59686, 59687, 59688, 59689};
        Selector selector = Selector.open();
        for (int port : ports) {
            ServerSocketChannel s = ServerSocketChannel.open();
            s.bind(new InetSocketAddress(port));
            s.configureBlocking(false);
            s.register(selector, SelectionKey.OP_ACCEPT);
        }
        while (true) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                if (key.isAcceptable()) {
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    SocketChannel client = server.accept();
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ, new ClientState());
                } else if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    ClientState state = (ClientState) key.attachment();
                    ByteBuffer buf = ByteBuffer.allocate(512);
                    int r;
                    try {
                        r = client.read(buf);
                    } catch (Exception e) {
                        key.cancel();
                        client.close();
                        continue;
                    }
                    if (r == -1) {
                        key.cancel();
                        client.close();
                        continue;
                    }
                    buf.flip();
                    String s = StandardCharsets.UTF_8.decode(buf).toString();
                    state.sb.append(s);
                    while (true) {
                        int idx = state.sb.indexOf("\n");
                        if (idx < 0) break;
                        String line = state.sb.substring(0, idx).trim();
                        state.sb.delete(0, idx + 1);
                        if (state.stage == 0) {
                            double x;
                            try {
                                x = Double.parseDouble(line);
                            } catch (Exception ex) {
                                writeAndClose(client, key, "Error: operands must be floats. Closing session.\n");
                                break;
                            }
                            state.x = x;
                            state.stage = 1;
                        } else if (state.stage == 1) {
                            double y;
                            try {
                                y = Double.parseDouble(line);
                            } catch (Exception ex) {
                                writeAndClose(client, key, "Error: operands must be floats. Closing session.\n");
                                break;
                            }
                            state.y = y;
                            String menu = "Choose an operation: 1. Addition (+) 2. Subtraction (-) 3. Multiplication (*) 4. Division (/)\n";
                            write(client, menu);
                            state.stage = 2;
                        } else if (state.stage == 2) {
                            boolean valid = true;
                            double result = 0.0;
                            if ("1".equals(line) || "+".equals(line)) {
                                result = state.x + state.y;
                            } else if ("2".equals(line) || "-".equals(line)) {
                                result = state.x - state.y;
                            } else if ("3".equals(line) || "*".equals(line)) {
                                result = state.x * state.y;
                            } else if ("4".equals(line) || "/".equals(line)) {
                                if (state.y == 0.0) {
                                    writeAndClose(client, key, "Error: Division by zero\n");
                                    break;
                                } else {
                                    result = state.x / state.y;
                                }
                            } else {
                                valid = false;
                            }
                            if (!valid) {
                                write(client, "Wrong choice! Choose again\n");
                            } else {
                                writeAndClose(client, key, String.valueOf(result) + "\n");
                            }
                        }
                    }
                }
            }
        }
    }

    static void write(SocketChannel c, String msg) {
        try {
            ByteBuffer b = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            while (b.hasRemaining()) c.write(b);
        } catch (Exception ignored) {}
    }

    static void writeAndClose(SocketChannel c, SelectionKey k, String msg) {
        write(c, msg);
        try {
            k.cancel();
            c.close();
        } catch (Exception ignored) {}
    }
}

class ClientState {
    int stage = 0;
    double x;
    double y;
    StringBuilder sb = new StringBuilder();
}
