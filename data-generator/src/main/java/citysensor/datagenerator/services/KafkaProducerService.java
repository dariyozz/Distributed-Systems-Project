package citysensor.datagenerator.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Send sensor reading to Kafka topic
     */
    public void sendSensorData(String topic, String city, Object sensorReading) {
        try {
            String json = objectMapper.writeValueAsString(sensorReading);
            
            kafkaTemplate.send(topic, city, json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send message to {}: {}", topic, ex.getMessage());
                    }
                });
                
        } catch (Exception e) {
            log.error("Error serializing sensor data: {}", e.getMessage());
        }
    }
}
