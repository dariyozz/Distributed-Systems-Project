package citysensor.datagenerator.consumer;

import com.sun.net.httpserver.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.*;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class DashboardServer {
    private static final int PORT = 8888;
    private static final Set<HttpExchange> sseClients = ConcurrentHashMap.newKeySet();

    // Allow local development - can be configured via environment variable
    private static final String ALLOWED_ORIGIN = System.getenv().getOrDefault("ALLOWED_ORIGIN", "http://localhost:5173");

    public static void main(String[] args) throws Exception {
        System.out.println("=== Dashboard Bridge Server Starting ===");
        System.out.println("Allowed CORS origin: " + ALLOWED_ORIGIN);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // CORS headers - configured for local development
        server.createContext("/stream", exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // SSE headers
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");

            exchange.sendResponseHeaders(200, 0);

            System.out.println("New client connected. Total: " + (sseClients.size() + 1));
            sseClients.add(exchange);

            // Keep connection alive
            try {
                while (sseClients.contains(exchange)) {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                sseClients.remove(exchange);
            }
        });

        // Health check endpoint
        server.createContext("/health", exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            String response = "{\"status\":\"healthy\",\"clients\":" + sseClients.size() + "}";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Dashboard Bridge running on http://localhost:" + PORT);
        System.out.println("Stream endpoint: http://localhost:" + PORT + "/stream");

        // Start Kafka consumer
        startKafkaConsumer();
    }

    private static void startKafkaConsumer() {
        String kafkaBootstrapServers = System.getenv().getOrDefault("KAFKA_BROKERS", "localhost:9092");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dashboard-bridge");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("smoke-fire", "vehicle-speed", "noise-level", "air-temp", "alerts"));

        System.out.println("Kafka consumer started, subscribed to topics (Kafka: " + kafkaBootstrapServers + ")");

        new Thread(() -> {
            while (true) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                    for (ConsumerRecord<String, String> record : records) {
                        String message = String.format(
                                "data: {\"type\":\"sensor-data\",\"topic\":\"%s\",\"data\":%s}\n\n",
                                record.topic(),
                                record.value()
                        );

                        broadcastToClients(message);
                    }
                } catch (Exception e) {
                    System.err.println("Error polling Kafka: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void broadcastToClients(String message) {
        Iterator<HttpExchange> iterator = sseClients.iterator();
        while (iterator.hasNext()) {
            HttpExchange client = iterator.next();
            try {
                OutputStream os = client.getResponseBody();
                os.write(message.getBytes());
                os.flush();
            } catch (IOException e) {
                System.out.println("Client disconnected");
                iterator.remove();
            }
        }
    }
}