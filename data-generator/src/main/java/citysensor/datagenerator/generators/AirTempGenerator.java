package citysensor.datagenerator.generators;

import citysensor.datagenerator.models.SensorReading;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AirTempGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AirTempGenerator.class);
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final Random random;
    private final String[] cities = {"New York", "Los Angeles", "Chicago", "Houston", "Phoenix"};

    private final Map<String, Double> lastTemp = new HashMap<>();
    private final Map<String, Double> lastHumidity = new HashMap<>();
    private final Map<String, Double> lastAQI = new HashMap<>();

    public AirTempGenerator(KafkaProducer<String, String> producer) {
        this.producer = producer;
        this.objectMapper = new ObjectMapper();
        this.random = new Random();

        for (String city : cities) {
            lastTemp.put(city, 20.0);
            lastHumidity.put(city, 60.0);
            lastAQI.put(city, 50.0);
        }
    }

    public void start() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            try {
                for (String city : cities) {
                    double temp = lastTemp.get(city) + (random.nextDouble() - 0.5) * 2;
                    temp = Math.max(5, Math.min(40, temp));
                    lastTemp.put(city, temp);

                    SensorReading tempReading = new SensorReading(
                            city, "temperature", "City-Center", temp
                    );
                    sendReading(tempReading);

                    double humidity = lastHumidity.get(city) + (random.nextDouble() - 0.5) * 5;
                    humidity = Math.max(30, Math.min(90, humidity));
                    lastHumidity.put(city, humidity);

                    SensorReading humidityReading = new SensorReading(
                            city, "humidity", "City-Center", humidity
                    );
                    sendReading(humidityReading);

                    double aqi = lastAQI.get(city) + (random.nextDouble() - 0.5) * 10;
                    aqi = Math.max(0, Math.min(300, aqi));
                    lastAQI.put(city, aqi);

                    SensorReading aqiReading = new SensorReading(
                            city, "air-quality", "City-Center", aqi
                    );
                    sendReading(aqiReading);
                }

                logger.info("Sent air quality, temperature, and humidity for {} cities",
                        cities.length);

            } catch (Exception e) {
                logger.error("Error in air/temp generator: {}", e.getMessage(), e);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void sendReading(SensorReading reading) {
        try {
            String json = objectMapper.writeValueAsString(reading);
            ProducerRecord<String, String> record =
                    new ProducerRecord<>("air-temp", reading.getCity(), json);
            producer.send(record);
        } catch (Exception e) {
            logger.error("Error sending reading: {}", e.getMessage());
        }
    }
}