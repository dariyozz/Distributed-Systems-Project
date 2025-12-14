package citysensor.flinkprocessor;

import citysensor.flinkprocessor.alerts.AlertDetector;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flink job for processing sensor data streams and generating alerts
 */
public class FlinkProcessor {
    private static final Logger logger = LoggerFactory.getLogger(FlinkProcessor.class);

    private static final String[] INPUT_TOPICS = {
            "smoke-fire", "vehicle-speed", "noise-level", "air-temp"
    };
    private static final String ALERT_TOPIC = "alerts";

    public static void main(String[] args) throws Exception {
        logger.info("=== City Sensor Flink Processor Starting ===");

        // Get Kafka bootstrap servers from environment or default to localhost
        String kafkaBootstrapServers = System.getenv().getOrDefault("KAFKA_BROKERS", "localhost:9092");
        logger.info("Kafka brokers: {}", kafkaBootstrapServers);

        // Set up Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(4);

        logger.info("Flink environment configured with parallelism: 4");

        // Configure Kafka sink for alerts
        KafkaSink<String> alertSink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(ALERT_TOPIC)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                .build();

        logger.info("Alert sink configured for topic: {}", ALERT_TOPIC);

        // Process each sensor type
        for (String topic : INPUT_TOPICS) {
            processSensorStream(env, topic, alertSink, kafkaBootstrapServers);
        }

        logger.info("=== All sensor streams configured ===");
        logger.info("Listening to topics: {}", String.join(", ", INPUT_TOPICS));
        logger.info("Publishing alerts to: {}", ALERT_TOPIC);

        // Execute the Flink job
        env.execute("City Sensor Monitoring System");
    }

    private static void processSensorStream(
            StreamExecutionEnvironment env,
            String topic,
            KafkaSink<String> alertSink,
            String kafkaBootstrapServers) {

        logger.info("Configuring stream for topic: {}", topic);

        // Configure Kafka source with unique group ID per topic
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setTopics(topic)
                .setGroupId("flink-processor-" + topic) // Unique group ID per topic
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Create data stream
        DataStream<String> stream = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), topic + "-source")
                .name(topic + "-input");

        // Apply alert detection
        DataStream<String> alerts = stream
                .map(new AlertDetector())
                .name(topic + "-alert-detection")
                .filter(alert -> alert != null) // Only keep actual alerts
                .name(topic + "-filter-alerts");

        // Sink alerts to Kafka
        alerts.sinkTo(alertSink).name(topic + "-alert-sink");

        logger.info("Stream processing configured for: {}", topic);
    }
}
