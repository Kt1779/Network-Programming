package tcpcalc;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class TCPServer {
    public static final int PORT = 12345; 
    private static final int BUF_SIZE = 1024;

    public static void main(String[] args) {
        System.out.println("UDP Server starting on port " + PORT + "...");
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            byte[] buf = new byte[BUF_SIZE];

            while (true) {
                DatagramPacket recvPacket = new DatagramPacket(buf, buf.length);
                socket.receive(recvPacket);
                InetAddress clientAddr = recvPacket.getAddress();
                int clientPort = recvPacket.getPort();
                String payload = new String(recvPacket.getData(), 0, recvPacket.getLength(), StandardCharsets.UTF_8);
                System.out.println("Received from " + clientAddr + ":" + clientPort + " -> '" + payload + "'");

                String[] lines = payload.split("\\r?\\n");
                if (lines.length < 2) {
                    String err = "Error: expected two operands separated by newline";
                    sendString(socket, err, clientAddr, clientPort);
                    System.out.println("Sent error to client: " + err);
                    continue;
                }

                double x, y;
                try {
                    x = Double.parseDouble(lines[0].trim());
                    y = Double.parseDouble(lines[1].trim());
                } catch (NumberFormatException nfe) {
                    String err = "Error: operands must be floats. Closing session.";
                    sendString(socket, err, clientAddr, clientPort);
                    System.out.println("Received invalid operands from client. Sent error and continue.");
                    continue;
                }

                String menu = "Choose an operation 1. Addition (+) 2. Substriction (-) 3. Multiplication (*) 4. Division (/)";
                sendString(socket, menu, clientAddr, clientPort);
                System.out.println("Sent menu to " + clientAddr + ":" + clientPort);

                boolean finished = false;
                while (!finished) {
                    DatagramPacket choicePacket = new DatagramPacket(buf, buf.length);
                    socket.receive(choicePacket);
                    if (!choicePacket.getAddress().equals(clientAddr) || choicePacket.getPort() != clientPort) {
                        System.out.println("Ignoring packet from " + choicePacket.getAddress() + ":" + choicePacket.getPort() + " while serving " + clientAddr + ":" + clientPort);
                        continue;
                    }

                    String choice = new String(choicePacket.getData(), 0, choicePacket.getLength(), StandardCharsets.UTF_8).trim();
                    boolean valid = true;
                    double result = 0.0;

                    switch (choice) {
                        case "1":
                        case "+":
                            result = x + y;
                            break;
                        case "2":
                        case "-":
                            result = x - y;
                            break;
                        case "3":
                        case "*":
                            result = x * y;
                            break;
                        case "4":
                        case "/":
                            if (y == 0.0) {
                                String err = "Error: Division by zero";
                                sendString(socket, err, clientAddr, clientPort);
                                System.out.println("Division by zero requested; informed client.");
                                finished = true; 
                                break;
                            } else {
                                result = x / y;
                                break;
                            }
                        default:
                            valid = false;
                            break;
                    }

                    if (!valid) {
                        String wrong = "Wrong choice! Choose again";
                        sendString(socket, wrong, clientAddr, clientPort);
                        System.out.println("Sent 'Wrong choice' to client.");
                    } else {
                        if (choice.equals("4") || choice.equals("/")) {
                            if (y == 0.0) {
                                break;
                            }
                        }
                        String resStr = String.valueOf(result);
                        sendString(socket, resStr, clientAddr, clientPort);
                        System.out.println("Sent result to client: " + resStr);
                        finished = true;
                    }
                } 

                System.out.println("Finished serving client " + clientAddr + ":" + clientPort);
            } 

        } catch (IOException e) {
            System.err.println("UDP Server error: " + e.getMessage());
        }
    }

    private static void sendString(DatagramSocket socket, String s, InetAddress addr, int port) throws IOException {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, addr, port);
        socket.send(sendPacket);
    }
}