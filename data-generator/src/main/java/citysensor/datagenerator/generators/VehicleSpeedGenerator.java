package citysensor.datagenerator.generators;

import citysensor.datagenerator.models.SensorReading;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Generates vehicle speed readings
 * Simulates vehicles passing with slight delays
 */
public class VehicleSpeedGenerator {
    private static final Logger logger = LoggerFactory.getLogger(VehicleSpeedGenerator.class);
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final Random random;
    private final String[] cities = {"New York", "Los Angeles", "Chicago", "Houston", "Phoenix"};
    private final int roadsPerCity = 20;

    public VehicleSpeedGenerator(KafkaProducer<String, String> producer) {
        this.producer = producer;
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
    }

    public void start() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            try {
                for (String city : cities) {
                    for (int road = 1; road <= roadsPerCity; road++) {
                        String location = "Road-" + road;

                        // Simulate 1-5 vehicles passing per interval
                        int vehicleCount = 1 + random.nextInt(5);

                        for (int v = 0; v < vehicleCount; v++) {
                            // Speed: 10-120 km/h, with occasional speeders (>100)
                            double speed = 30 + random.nextDouble() * 70;
                            if (random.nextDouble() < 0.01) { // 1% speeders
                                speed = 100 + random.nextDouble() * 50;
                            }

                            SensorReading reading = new SensorReading(
                                    city,
                                    "vehicle-speed",
                                    location,
                                    speed
                            );

                            String json = objectMapper.writeValueAsString(reading);
                            ProducerRecord<String, String> record =
                                    new ProducerRecord<>("vehicle-speed", city, json);

                            producer.send(record);

                            // Small delay between vehicles (50-200ms)
                            Thread.sleep(50 + random.nextInt(150));
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error generating vehicle speed data: {}", e.getMessage());
            }
        }, 0, 4, TimeUnit.SECONDS); // Every 4 seconds

        logger.info("Vehicle Speed Generator started.");

    }
}