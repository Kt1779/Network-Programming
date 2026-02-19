package WeatherAppPackage;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class WeatherClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 23674;
    private static final int RECONNECT_DELAY = 5000; // 5 seconds delay
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            try {
                System.out.println("Attempting to connect to Weather Server...");
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                System.out.println("Connected to Weather Server");
                System.out.println("[get weather: \"GET WEATHER <city> <days:optional>\"]");
                System.out.println("[disconnect: \"EXIT\"]");
                System.out.println();
                
                try (
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
                ) {
                    while (true) {
                        System.out.print("Enter command: ");
                        String command = scanner.nextLine().trim();
                        
                        if (command.isEmpty()) {
                            continue;
                        }
                        
                        // sending request to server
                        out.println(command);
                        
                        // exit command
                        if (command.equalsIgnoreCase("EXIT")) {
                            String response = in.readLine();
                            System.out.println(response);
                            scanner.close();
                            socket.close();
                            return;
                        }
                        
                        // get and output response
                        String response = in.readLine();
                        
                        if (response == null) {
                            System.out.println("Connection lost to server.");
                            break;
                        }
                        
                        if (response.startsWith("Forecast for")) {
                            System.out.println(response);
                            while (in.ready()) {
                                String line = in.readLine();
                                if (line != null && !line.isEmpty()) {
                                    System.out.println(line);
                                }
                            }
                        } else {
                            System.out.println("Response: " + response);
                        }
                        
                        System.out.println();
                    }
                } catch (IOException e) {
                    System.out.println("Connection error: " + e.getMessage());
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // skip
                    }
                }
                
                // reconnect attempt
                System.out.println("Reconnecting in " + (RECONNECT_DELAY / 1000) + " seconds...");
                Thread.sleep(RECONNECT_DELAY);
                
            } catch (UnknownHostException e) {
                System.err.println("Cannot find server: " + SERVER_HOST);
                System.out.println("Retrying in " + (RECONNECT_DELAY / 1000) + " seconds...");
                try {
                    Thread.sleep(RECONNECT_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (IOException e) {
                System.err.println("Cannot connect to server: " + e.getMessage());
                System.out.println("Retrying in " + (RECONNECT_DELAY / 1000) + " seconds...");
                try {
                    Thread.sleep(RECONNECT_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Reconnection interrupted. Exiting...");
                break;
            }
        }
        
        scanner.close();
    }
}