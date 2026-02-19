package WeatherAppPackage;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class WeatherServer {
    // API KEY
    private static final String API_KEY = "zpka_1b35c184add140b6951e68d373fb71e7_b076bd4e";
    
    private static final int PORT = 23674;
    private static final int THREAD_POOL_SIZE = 4;
    private static final String BASE_URL = "https://dataservice.accuweather.com";
    
    // fetch weather data
    private static final ConcurrentHashMap<String, String> weatherCache = new ConcurrentHashMap<>();
    // fetch forecast data
    private static final ConcurrentHashMap<String, String> forecastCache = new ConcurrentHashMap<>();
    // fetch location keys
    private static final ConcurrentHashMap<String, String> locationKeyCache = new ConcurrentHashMap<>();
    
    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Weather Server started on port " + PORT);
            System.out.println("Waiting for clients...");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }
    
    static class ClientHandler implements Runnable {
        private Socket socket;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String request;
                while ((request = in.readLine()) != null) {
                    System.out.println("Received: " + request);
                    
                    if (request.trim().equalsIgnoreCase("EXIT")) {
                        out.println("Connection closed. Goodbye!");
                        break;
                    }
                    
                    String response = processRequest(request.trim());
                    out.println(response);
                }
            } catch (IOException e) {
                System.err.println("Client handler error: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                    System.out.println("Client disconnected");
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            }
        }
        
        private String processRequest(String request) {
            String[] parts = request.split("\\s+");
            
            if (parts.length < 3) {
                return "Invalid command. Use: GET WEATHER <cityName> or GET FORECAST <cityName> <days>";
            }
            
            if (!parts[0].equalsIgnoreCase("GET")) {
                return "Invalid command. Must start with GET";
            }
            
            String command = parts[1].toUpperCase();
            String cityName = parts[2].toLowerCase();
            
            if (command.equals("WEATHER")) {
                return getWeather(cityName);
            } else if (command.equals("FORECAST")) {
                int days = 1;
                if (parts.length > 3) {
                    try {
                        days = Integer.parseInt(parts[3]);
                        if (days <= 0) {
                            return "Days parameter must be positive";
                        }
                        if (days > 5) {
                            days = 5;
                        }
                    } catch (NumberFormatException e) {
                        return "Invalid days parameter";
                    }
                }
                return getForecast(cityName, days);
            } else {
                return "Invalid command. Use WEATHER or FORECAST";
            }
        }
        
        private String getWeather(String cityName) {
            if (weatherCache.containsKey(cityName)) {
                System.out.println("Returning cached weather for: " + cityName);
                return weatherCache.get(cityName);
            }
            
            // get location key
            String locationKey = getLocationKey(cityName);
            if (locationKey == null) {
                return "Requested City Not Found";
            }
            
            // fetch current weather
            try {
                String url = BASE_URL + "/currentconditions/v1/" + locationKey + "?apikey=" + API_KEY + "&details=true";
                String jsonResponse = fetchData(url);
                
                if (jsonResponse == null) {
                    return "Unable To Fetch Data";
                }
                
                // JSON parsing
                double temp = extractValue(jsonResponse, "\"Temperature\"", "\"Metric\"", "\"Value\"");
                double realFeelTemp = extractValue(jsonResponse, "\"RealFeelTemperature\"", "\"Metric\"", "\"Value\"");
                int humidity = (int) extractSimpleValue(jsonResponse, "\"RelativeHumidity\"");
                
                if (temp == -999 || realFeelTemp == -999 || humidity == -999) {
                    return "Unable To Fetch Data";
                }
                
                String response = String.format("{City: %s, Temperature: %.1f°C, Humidity: %d%%, RealFeel: %.1f°C}",
                    cityName, temp, humidity, realFeelTemp);
                
                weatherCache.put(cityName, response);
                return response;
                
            } catch (Exception e) {
                System.err.println("Error fetching weather: " + e.getMessage());
                return "Unable To Fetch Data";
            }
        }
        
        private String getForecast(String cityName, int days) {
            String cacheKey = cityName + "_" + days;
            
            if (forecastCache.containsKey(cacheKey)) {
                System.out.println("Returning cached forecast for: " + cityName);
                return forecastCache.get(cacheKey);
            }
            
            // get location key
            String locationKey = getLocationKey(cityName);
            if (locationKey == null) {
                return "Requested City Not Found";
            }
            
            // fetch forecast
            try {
                String url = BASE_URL + "/forecasts/v1/daily/5day/" + locationKey + "?apikey=" + API_KEY + "&metric=true";
                String jsonResponse = fetchData(url);
                
                if (jsonResponse == null) {
                    return "Unable To Fetch Data";
                }
                
                StringBuilder response = new StringBuilder();
                response.append(String.format("Forecast for %s (%d day%s):\n", 
                    cityName, days, days > 1 ? "s" : ""));
                
                // parse forecast array
                String[] dailyForecasts = extractArray(jsonResponse, "\"DailyForecasts\"");
                
                for (int i = 0; i < Math.min(days, dailyForecasts.length); i++) {
                    String dayData = dailyForecasts[i];
                    
                    String date = extractString(dayData, "\"Date\"");
                    if (date.length() > 10) {
                        date = date.substring(0, 10);
                    }
                    
                    double minTemp = extractNestedValue(dayData, "\"Temperature\"", "\"Minimum\"", "\"Value\"");
                    double maxTemp = extractNestedValue(dayData, "\"Temperature\"", "\"Maximum\"", "\"Value\"");
                    String conditions = extractNestedString(dayData, "\"Day\"", "\"IconPhrase\"");
                    
                    if (minTemp == -999 || maxTemp == -999) {
                        continue;
                    }
                    
                    response.append(String.format("Day %d (%s): Min: %.1f°C, Max: %.1f°C, Conditions: %s\n",
                        i + 1, date, minTemp, maxTemp, conditions));
                }
                
                String finalResponse = response.toString().trim();
                forecastCache.put(cacheKey, finalResponse);
                return finalResponse;
                
            } catch (Exception e) {
                System.err.println("Error fetching forecast: " + e.getMessage());
                return "Unable To Fetch Data";
            }
        }
        
        private String getLocationKey(String cityName) {
            if (locationKeyCache.containsKey(cityName)) {
                return locationKeyCache.get(cityName);
            }
            
            try {
                String encodedCity = URLEncoder.encode(cityName, "UTF-8");
                String url = BASE_URL + "/locations/v1/cities/search?apikey=" + API_KEY + "&q=" + encodedCity;
                
                System.out.println("Searching for city: " + cityName);
                String jsonResponse = fetchData(url);
                
                if (jsonResponse == null) {
                    System.out.println("No response from API");
                    return null;
                }
                
                System.out.println("API Response: " + jsonResponse.substring(0, Math.min(200, jsonResponse.length())));
                
                if (jsonResponse.trim().equals("[]") || jsonResponse.trim().isEmpty()) {
                    System.out.println("City not found in API");
                    return null;
                }
                
                String locationKey = extractString(jsonResponse, "\"Key\"");
                if (locationKey.isEmpty()) {
                    System.out.println("Could not extract location key");
                    return null;
                }
                
                System.out.println("Found location key: " + locationKey);
                locationKeyCache.put(cityName, locationKey);
                return locationKey;
                
            } catch (Exception e) {
                System.err.println("Error getting location key: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        
        private String fetchData(String urlString) {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    return null;
                }
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                return response.toString();
                
            } catch (Exception e) {
                System.err.println("Error fetching data: " + e.getMessage());
                return null;
            }
        }
        
        private String extractString(String json, String key) {
            int keyIndex = json.indexOf(key);
            if (keyIndex == -1) return "";
            
            int startIndex = json.indexOf("\"", keyIndex + key.length() + 1);
            if (startIndex == -1) return "";
            
            int endIndex = json.indexOf("\"", startIndex + 1);
            if (endIndex == -1) return "";
            
            return json.substring(startIndex + 1, endIndex);
        }
        
        private double extractSimpleValue(String json, String key) {
            try {
                int keyIndex = json.indexOf(key);
                if (keyIndex == -1) return -999;
                
                int colonIndex = json.indexOf(":", keyIndex);
                if (colonIndex == -1) return -999;
                
                int commaIndex = json.indexOf(",", colonIndex);
                int braceIndex = json.indexOf("}", colonIndex);
                
                int endIndex;
                if (commaIndex == -1 && braceIndex == -1) return -999;
                else if (commaIndex == -1) endIndex = braceIndex;
                else if (braceIndex == -1) endIndex = commaIndex;
                else endIndex = Math.min(commaIndex, braceIndex);
                
                String valueStr = json.substring(colonIndex + 1, endIndex).trim();
                return Double.parseDouble(valueStr);
            } catch (Exception e) {
                return -999;
            }
        }
        
        private double extractValue(String json, String key1, String key2, String key3) {
            try {
                int idx1 = json.indexOf(key1);
                if (idx1 == -1) return -999;
                
                int idx2 = json.indexOf(key2, idx1);
                if (idx2 == -1) return -999;
                
                int idx3 = json.indexOf(key3, idx2);
                if (idx3 == -1) return -999;
                
                int colonIndex = json.indexOf(":", idx3);
                if (colonIndex == -1) return -999;
                
                int commaIndex = json.indexOf(",", colonIndex);
                int braceIndex = json.indexOf("}", colonIndex);
                
                int endIndex;
                if (commaIndex == -1 && braceIndex == -1) return -999;
                else if (commaIndex == -1) endIndex = braceIndex;
                else if (braceIndex == -1) endIndex = commaIndex;
                else endIndex = Math.min(commaIndex, braceIndex);
                
                String valueStr = json.substring(colonIndex + 1, endIndex).trim();
                return Double.parseDouble(valueStr);
            } catch (Exception e) {
                return -999;
            }
        }
        
        private double extractNestedValue(String json, String key1, String key2, String key3) {
            return extractValue(json, key1, key2, key3);
        }
        
        private String extractNestedString(String json, String key1, String key2) {
            try {
                int idx1 = json.indexOf(key1);
                if (idx1 == -1) return "";
                
                int idx2 = json.indexOf(key2, idx1);
                if (idx2 == -1) return "";
                
                int startIndex = json.indexOf("\"", idx2 + key2.length() + 1);
                if (startIndex == -1) return "";
                
                int endIndex = json.indexOf("\"", startIndex + 1);
                if (endIndex == -1) return "";
                
                return json.substring(startIndex + 1, endIndex);
            } catch (Exception e) {
                return "";
            }
        }
        
        private String[] extractArray(String json, String arrayKey) {
            try {
                int keyIndex = json.indexOf(arrayKey);
                if (keyIndex == -1) return new String[0];
                
                int arrayStart = json.indexOf("[", keyIndex);
                if (arrayStart == -1) return new String[0];
                
                int arrayEnd = json.indexOf("]", arrayStart);
                if (arrayEnd == -1) return new String[0];
                
                String arrayContent = json.substring(arrayStart + 1, arrayEnd);
                
                List<String> objects = new ArrayList<>();
                int depth = 0;
                int start = 0;
                
                for (int i = 0; i < arrayContent.length(); i++) {
                    char c = arrayContent.charAt(i);
                    if (c == '{') {
                        if (depth == 0) start = i;
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            objects.add(arrayContent.substring(start, i + 1));
                        }
                    }
                }
                
                return objects.toArray(new String[0]);
            } catch (Exception e) {
                return new String[0];
            }
        }
    }
}