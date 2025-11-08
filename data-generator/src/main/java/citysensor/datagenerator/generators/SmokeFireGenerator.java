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

public class SmokeFireGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SmokeFireGenerator.class);
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final Random random;
    private final String[] cities = {"New York", "Los Angeles", "Chicago", "Houston", "Phoenix"};
    private final int buildingsPerCity = 500;

    public SmokeFireGenerator(KafkaProducer<String, String> producer) {
        this.producer = producer;
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
    }

    public void start() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            try {
                for (String city : cities) {
                    for (int building = 1; building <= buildingsPerCity; building++) {
                        String location = "Building-" + building;

                        double smokeLevel = random.nextDouble() < 0.999
                                ? random.nextDouble() * 20
                                : 80 + random.nextDouble() * 20;

                        SensorReading reading = new SensorReading(
                                city,
                                "smoke-fire",
                                location,
                                smokeLevel
                        );

                        String json = objectMapper.writeValueAsString(reading);
                        ProducerRecord<String, String> record =
                                new ProducerRecord<>("smoke-fire", city, json);

                        producer.send(record, (metadata, exception) -> {
                            if (exception != null) {
                                logger.error("Error sending smoke/fire data for {}: {}",
                                        city, exception.getMessage());
                            }
                        });
                    }
                }
                logger.info("Sent smoke/fire readings for {} cities, {} buildings each",
                        cities.length, buildingsPerCity);

            } catch (Exception e) {
                logger.error("Error in smoke/fire generator: {}", e.getMessage(), e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
}