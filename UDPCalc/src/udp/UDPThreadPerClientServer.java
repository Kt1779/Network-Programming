package udp;
	
	import java.net.*;
	import java.io.*;
	import java.util.concurrent.*;
	import java.util.*;

	public class UDPThreadPerClientServer {
	    public static void main(String[] args) throws Exception {
	        DatagramSocket socket = new DatagramSocket(9876);
	        ConcurrentMap<SocketAddress, LinkedBlockingQueue<String>> queues = new ConcurrentHashMap<>();
	        while (true) {
	            byte[] buf = new byte[1024];
	            DatagramPacket packet = new DatagramPacket(buf, buf.length);
	            socket.receive(packet);
	            String data = new String(packet.getData(), 0, packet.getLength()).trim();
	            SocketAddress clientAddr = new InetSocketAddress(packet.getAddress(), packet.getPort());
	            String[] parts = data.split("\\s+");
	            boolean isInitial = false;
	            if (parts.length >= 2) {
	                try {
	                    Float.parseFloat(parts[0]);
	                    Float.parseFloat(parts[1]);
	                    isInitial = true;
	                } catch (Exception e) { isInitial = false; }
	            }
	            if (isInitial) {
	                LinkedBlockingQueue<String> q = new LinkedBlockingQueue<>(1);
	                queues.put(clientAddr, q);
	                String prompt = "Select an operation\n1. Addition (+)\n2. Subtraction (-)\n3. Multiplication (*)\n4. Division (/)";
	                byte[] pbytes = prompt.getBytes("UTF-8");
	                InetAddress addr = packet.getAddress();
	                int port = packet.getPort();
	                socket.send(new DatagramPacket(pbytes, pbytes.length, addr, port));
	                final float x = Float.parseFloat(parts[0]);
	                final float y = Float.parseFloat(parts[1]);
	                new Thread(() -> {
	                    try {
	                        String choice = q.poll(60, TimeUnit.SECONDS);
	                        String resp;
	                        if (choice == null) {
	                            resp = "Incorrect choice!";
	                        } else {
	                            float result;
	                            switch (choice) {
	                                case "1": case "+": result = x + y; break;
	                                case "2": case "-": result = x - y; break;
	                                case "3": case "*": result = x * y; break;
	                                case "4": case "/": result = x / y; break;
	                                default: resp = "Incorrect choice!"; byte[] rb = resp.getBytes("UTF-8"); socket.send(new DatagramPacket(rb, rb.length, addr, port)); queues.remove(clientAddr); return;
	                            }
	                            resp = Float.toString(result);
	                        }
	                        byte[] rb = resp.getBytes("UTF-8");
	                        socket.send(new DatagramPacket(rb, rb.length, addr, port));
	                    } catch (Exception e) { }
	                    queues.remove(clientAddr);
	                }).start();
	            } else {
	                LinkedBlockingQueue<String> q = queues.get(clientAddr);
	                if (q != null) q.offer(data);
	            }
	        }
	    }
	}
