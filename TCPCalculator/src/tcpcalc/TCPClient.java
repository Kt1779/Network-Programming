package tcpcalc;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.net.SocketTimeoutException;

public class TCPClient {
    public static final String HOST = "localhost";
    public static final int PORT = 12345; 
    private static final int BUF_SIZE = 1024;
    private static final int RECEIVE_TIMEOUT_MS = 10000;

    public static void main(String[] args) {
        Scanner console = new Scanner(System.in);
        System.out.println("UDP Client: Enter operand X (float): ");
        String x = console.nextLine().trim();
        System.out.println("UDP Client: Enter operand Y (float): ");
        String y = console.nextLine().trim();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(RECEIVE_TIMEOUT_MS);
            InetAddress serverAddr = InetAddress.getByName(HOST);

            String operands = x + "\n" + y;
            sendString(socket, operands, serverAddr, PORT);

            String menu = receiveString(socket);
            if (menu == null) {
                System.out.println("No response from server (timeout). Exiting.");
                return;
            }
            System.out.println("Server says: " + menu);

            while (true) {
                System.out.print("Enter choice (1/2/3/4 or + - * /): ");
                String choice = console.nextLine().trim();
                sendString(socket, choice, serverAddr, PORT);

                String response = receiveString(socket);
                if (response == null) {
                    System.out.println("No response from server (timeout). Exiting.");
                    break;
                }

                if (response.startsWith("Wrong choice!")) {
                    System.out.println("Server: " + response);
                } else {
                    System.out.println("Server response: " + response);
                    break;
                }
            }

        } catch (SocketTimeoutException ste) {
            System.err.println("Timed out waiting for server response: " + ste.getMessage());
        } catch (IOException ioe) {
            System.err.println("I/O error: " + ioe.getMessage());
        } finally {
            console.close();
        }
    }

    private static void sendString(DatagramSocket socket, String s, InetAddress addr, int port) throws IOException {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        socket.send(packet);
    }

    private static String receiveString(DatagramSocket socket) throws IOException {
        byte[] buf = new byte[BUF_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        try {
            socket.receive(packet);
        } catch (SocketTimeoutException ste) {
            throw ste;
        }
        return new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
    }
}