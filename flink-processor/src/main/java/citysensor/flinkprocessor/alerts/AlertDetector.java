package citysensor.flinkprocessor.alerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects critical conditions and generates alerts
 */
public class AlertDetector extends RichMapFunction<String, String> {
    private static final Logger logger = LoggerFactory.getLogger(AlertDetector.class);
    private transient ObjectMapper objectMapper;

    // Alert thresholds
    private static final double FIRE_THRESHOLD = 70.0;
    private static final double SPEED_THRESHOLD = 100.0;
    private static final double NOISE_THRESHOLD = 90.0;
    private static final double AQI_UNHEALTHY = 150.0;
    private static final double TEMP_EXTREME_LOW = 0.0;
    private static final double TEMP_EXTREME_HIGH = 38.0;

    @Override
    public void open(Configuration parameters) throws Exception {
        objectMapper = new ObjectMapper();
    }

    @Override
    public String map(String value) throws Exception {
        try {
            JsonNode node = objectMapper.readTree(value);
            String sensorType = node.get("sensor_type").asText();
            double sensorValue = node.get("value").asDouble();
            String city = node.get("city").asText();
            String location = node.get("location").asText();

            // Check if value exceeds threshold
            AlertLevel level = checkThreshold(sensorType, sensorValue);

            if (level != AlertLevel.NORMAL) {
                ObjectNode alert = objectMapper.createObjectNode();
                alert.put("alert_type", sensorType);
                alert.put("severity", level.toString());
                alert.put("city", city);
                alert.put("location", location);
                alert.put("value", sensorValue);
                alert.put("threshold", getThreshold(sensorType));
                alert.put("timestamp", System.currentTimeMillis());
                alert.put("message", generateMessage(sensorType, city, location, sensorValue, level));

                String alertJson = objectMapper.writeValueAsString(alert);
                logger.warn("ALERT GENERATED: {} - {} in {} at {}",
                        level, sensorType, city, location);
                return alertJson;
            }

            return null; // No alert needed

        } catch (Exception e) {
            logger.error("Error processing sensor reading: {}", e.getMessage());
            return null;
        }
    }

    private AlertLevel checkThreshold(String sensorType, double value) {
        switch (sensorType) {
            case "smoke-fire":
                if (value >= FIRE_THRESHOLD) return AlertLevel.CRITICAL;
                break;
            case "vehicle-speed":
                if (value >= SPEED_THRESHOLD + 30) return AlertLevel.CRITICAL;
                if (value >= SPEED_THRESHOLD) return AlertLevel.WARNING;
                break;
            case "noise-level":
                if (value >= NOISE_THRESHOLD + 10) return AlertLevel.CRITICAL;
                if (value >= NOISE_THRESHOLD) return AlertLevel.WARNING;
                break;
            case "air-quality":
                if (value >= AQI_UNHEALTHY + 50) return AlertLevel.CRITICAL;
                if (value >= AQI_UNHEALTHY) return AlertLevel.WARNING;
                break;
            case "temperature":
                if (value <= TEMP_EXTREME_LOW || value >= TEMP_EXTREME_HIGH)
                    return AlertLevel.WARNING;
                break;
        }
        return AlertLevel.NORMAL;
    }

    private double getThreshold(String sensorType) {
        switch (sensorType) {
            case "smoke-fire": return FIRE_THRESHOLD;
            case "vehicle-speed": return SPEED_THRESHOLD;
            case "noise-level": return NOISE_THRESHOLD;
            case "air-quality": return AQI_UNHEALTHY;
            case "temperature": return TEMP_EXTREME_HIGH;
            default: return 0.0;
        }
    }

    private String generateMessage(String type, String city, String location,
                                   double value, AlertLevel level) {
        switch (type) {
            case "smoke-fire":
                return String.format("%s: Fire detected at %s, %s! Smoke level: %.1f",
                        level, location, city, value);
            case "vehicle-speed":
                return String.format("%s: Excessive speed at %s, %s! Vehicle: %.0f km/h",
                        level, location, city, value);
            case "noise-level":
                return String.format("%s: Excessive noise at %s, %s! Level: %.1f dB",
                        level, location, city, value);
            case "air-quality":
                return String.format("%s: Poor air quality in %s! AQI: %.0f",
                        level, city, value);
            case "temperature":
                return String.format("%s: Extreme temperature in %s! Temp: %.1f°C",
                        level, city, value);
            default:
                return String.format("%s alert in %s", level, city);
        }
    }

    enum AlertLevel {
        NORMAL, WARNING, CRITICAL
    }
}