package citysensor.datagenerator.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {
    
    private final DatabaseService databaseService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Create a sink for broadcasting sensor data to multiple subscribers
    private final Sinks.Many<String> sensorDataSink = Sinks.many().multicast().onBackpressureBuffer();
    
    /**
     * Listen to smoke-fire topic
     */
    @KafkaListener(topics = "smoke-fire", groupId = "dashboard-bridge")
    public void consumeSmokeFire(String message) {
        processMessage(message, "smoke-fire");
    }
    
    /**
     * Listen to vehicle-speed topic
     */
    @KafkaListener(topics = "vehicle-speed", groupId = "dashboard-bridge")
    public void consumeVehicleSpeed(String message) {
        processMessage(message, "vehicle-speed");
    }
    
    /**
     * Listen to noise-level topic
     */
    @KafkaListener(topics = "noise-level", groupId = "dashboard-bridge")
    public void consumeNoiseLevel(String message) {
        processMessage(message, "noise-level");
    }
    
    /**
     * Listen to air-temp topic
     */
    @KafkaListener(topics = "air-temp", groupId = "dashboard-bridge")
    public void consumeAirTemp(String message) {
        processMessage(message, "air-temp");
    }
    
    /**
     * Listen to alerts topic and persist to database
     */
    @KafkaListener(topics = "alerts", groupId = "dashboard-bridge")
    public void consumeAlerts(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            
            // Persist critical alerts to database
            String severity = node.get("severity").asText();
            if ("CRITICAL".equals(severity) || "WARNING".equals(severity)) {
                String alertType = node.get("alert_type").asText();
                String city = node.get("city").asText();
                String location = node.get("location").asText();
                Double value = node.get("value").asDouble();
                Double threshold = node.get("threshold").asDouble();
                String alertMessage = node.get("message").asText();
                
                // Save to database asynchronously
                databaseService.saveAlert(alertType, severity, city, location, value, threshold, alertMessage)
                    .subscribe(
                        saved -> log.debug("Alert persisted: {}", saved.getId()),
                        error -> log.error("Failed to persist alert: {}", error.getMessage())
                    );
            }
            
            // Broadcast to SSE clients
            processMessage(message, "alerts");
            
        } catch (Exception e) {
            log.error("Error processing alert: {}", e.getMessage());
        }
    }
    
    /**
     * Process and broadcast message to SSE clients
     */
    private void processMessage(String message, String topic) {
        try {
            String wrappedMessage = String.format(
                "{\"type\":\"sensor-data\",\"topic\":\"%s\",\"data\":%s}",
                topic,
                message
            );
            
            // Emit to all SSE subscribers
            sensorDataSink.tryEmitNext(wrappedMessage);
            
        } catch (Exception e) {
            log.error("Error processing message from {}: {}", topic, e.getMessage());
        }
    }
    
    /**
     * Get the reactive stream for SSE
     */
    public Flux<String> getSensorDataStream() {
        return sensorDataSink.asFlux();
    }
}
