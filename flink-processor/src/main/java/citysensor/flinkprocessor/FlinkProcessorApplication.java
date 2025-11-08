package citysensor.flinkprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FlinkProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlinkProcessorApplication.class, args);
        try {
            FlinkProcessor.main(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
