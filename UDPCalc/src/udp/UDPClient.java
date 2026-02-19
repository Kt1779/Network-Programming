package udp;
import java.net.*;
import java.io.*;
import java.util.Scanner;

public class UDPClient {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        System.out.print("Server host: ");
        String serverHost = sc.nextLine();

        System.out.print("Server port: ");
        int port = Integer.parseInt(sc.nextLine());

        System.out.print("Enter X: ");
        String x = sc.nextLine();

        System.out.print("Enter Y: ");
        String y = sc.nextLine();

        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(serverHost);

        String message = x + " " + y;
        byte[] buf = message.getBytes();

        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        socket.send(packet);

        byte[] recvBuf = new byte[1024];
        DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
        socket.receive(recvPacket);

        String menu = new String(recvPacket.getData(), 0, recvPacket.getLength());
        System.out.println(menu);

        System.out.print("Your choice: ");
        String choice = sc.nextLine();

        byte[] cbuf = choice.getBytes();
        DatagramPacket cpacket = new DatagramPacket(cbuf, cbuf.length, address, port);
        socket.send(cpacket);

        byte[] resBuf = new byte[1024];
        DatagramPacket resPacket = new DatagramPacket(resBuf, resBuf.length);
        socket.receive(resPacket);

        String result = new String(resPacket.getData(), 0, resPacket.getLength());
        System.out.println("Result: " + result);

        socket.close();
    }
}
