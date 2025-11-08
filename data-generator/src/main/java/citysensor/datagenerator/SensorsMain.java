package citysensor.datagenerator;


import citysensor.datagenerator.generators.AirTempGenerator;
import citysensor.datagenerator.generators.NoiseGenerator;
import citysensor.datagenerator.generators.SmokeFireGenerator;
import citysensor.datagenerator.generators.VehicleSpeedGenerator;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Main application - starts all sensor data generators
 * This simulates sensor data from 5 cities and sends to Kafka
 */
public class SensorsMain {
    private static final Logger logger = LoggerFactory.getLogger(SensorsMain.class);

    public static void main(String[] args) {
        logger.info("=== City Sensor Data Generator Starting ===");

        // Get Kafka bootstrap servers from environment or default to localhost
        String kafkaBootstrapServers = System.getenv().getOrDefault("KAFKA_BROKERS", "localhost:9092");

        // Configure Kafka Producer
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        logger.info("Kafka producer configured for {}", kafkaBootstrapServers);

        // Initialize and start all generators
        try {
            SmokeFireGenerator smokeFireGen = new SmokeFireGenerator(producer);
            VehicleSpeedGenerator vehicleGen = new VehicleSpeedGenerator(producer);
            NoiseGenerator noiseGen = new NoiseGenerator(producer);
            AirTempGenerator airTempGen = new AirTempGenerator(producer);

            logger.info("Starting smoke/fire sensor generator (1s interval)...");
            smokeFireGen.start();

            logger.info("Starting vehicle speed sensor generator (variable interval)...");
            vehicleGen.start();

            logger.info("Starting noise level sensor generator (1s interval)...");
            noiseGen.start();

            logger.info("Starting air quality/temp/humidity generator (10s interval)...");
            airTempGen.start();

            logger.info("=== All generators started successfully ===");
            logger.info("Monitoring cities: New York, Los Angeles, Chicago, Houston, Phoenix");
            logger.info("Press Ctrl+C to stop");

            // Keep application running
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down generators...");
                producer.close();
                logger.info("Shutdown complete");
            }));

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Error starting generators: {}", e.getMessage(), e);
            producer.close();
            System.exit(1);
        }
    }
}