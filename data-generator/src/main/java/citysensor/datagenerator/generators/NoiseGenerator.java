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

public class NoiseGenerator {
    private static final Logger logger = LoggerFactory.getLogger(NoiseGenerator.class);
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final Random random;
    private final String[] cities = {"New York", "Los Angeles", "Chicago", "Houston", "Phoenix"};
    private final String[] locations = {
            "Nightclub-Downtown", "Nightclub-West", "Construction-Site-A",
            "Construction-Site-B", "Stadium", "Concert-Hall", "Airport-Area"
    };

    public NoiseGenerator(KafkaProducer<String, String> producer) {
        this.producer = producer;
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
    }

    public void start() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            try {
                for (String city : cities) {
                    for (String location : locations) {
                        double baseNoise = location.contains("Nightclub") ? 80 :
                                location.contains("Construction") ? 75 : 60;

                        double noiseLevel = baseNoise + (random.nextDouble() - 0.5) * 16;
                        noiseLevel = Math.max(40, Math.min(100, noiseLevel));

                        SensorReading reading = new SensorReading(
                                city,
                                "noise-level",
                                location,
                                noiseLevel
                        );

                        String json = objectMapper.writeValueAsString(reading);
                        ProducerRecord<String, String> record =
                                new ProducerRecord<>("noise-level", city, json);

                        producer.send(record);
                    }
                }
                logger.info("Sent noise level readings for {} cities, {} locations each",
                        cities.length, locations.length);

            } catch (Exception e) {
                logger.error("Error in noise generator: {}", e.getMessage(), e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
}