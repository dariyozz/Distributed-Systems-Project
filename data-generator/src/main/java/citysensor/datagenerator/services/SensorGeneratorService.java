package citysensor.datagenerator.services;

import citysensor.datagenerator.models.SensorReading;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorGeneratorService {
    
    private final KafkaProducerService kafkaProducerService;
    private final Random random = new Random();
    private final String[] cities = {"New York", "Los Angeles", "Chicago", "Houston", "Phoenix"};
    private final Map<String, Double> cityTemperatures = new ConcurrentHashMap<>();
    
    /**
     * Generate smoke/fire sensor data every 1 second
     */
    @Scheduled(fixedRate = 1000)
    public void generateSmokeFire() {
        for (String city : cities) {
            for (int building = 1; building <= 500; building++) {
                String location = "Building-" + building;
                
                // 99.9% safe, 0.1% fire risk
                double smokeLevel = random.nextDouble() < 0.999
                    ? random.nextDouble() * 20
                    : 80 + random.nextDouble() * 20;
                
                SensorReading reading = new SensorReading(city, "smoke-fire", location, smokeLevel);
                kafkaProducerService.sendSensorData("smoke-fire", city, reading);
            }
        }
        log.info("Sent smoke/fire readings for {} cities, 500 buildings each", cities.length);
    }
    
    /**
     * Generate vehicle speed data every 4 seconds
     */
    @Scheduled(fixedRate = 4000)
    public void generateVehicleSpeed() {
        for (String city : cities) {
            for (int road = 1; road <= 20; road++) {
                String location = "Road-" + road;
                
                // Simulate 1-5 vehicles per interval
                int vehicleCount = 1 + random.nextInt(5);
                
                for (int v = 0; v < vehicleCount; v++) {
                    // Speed: Gaussian distribution (Mean: 60, SD: 10)
                    double speed = 60 + random.nextGaussian() * 10;
                    
                    // 0.1% chance of extreme speeding (>100 km/h)
                    if (random.nextDouble() < 0.001) {
                        speed = 100 + random.nextDouble() * 50;
                    }
                    
                    // Ensure speed is not negative
                    speed = Math.max(0, speed);
                    
                    SensorReading reading = new SensorReading(city, "vehicle-speed", location, speed);
                    kafkaProducerService.sendSensorData("vehicle-speed", city, reading);
                    
                    try {
                        Thread.sleep(50 + random.nextInt(150)); // Delay between vehicles
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
    
    /**
     * Generate noise level data every 1 second
     */
    @Scheduled(fixedRate = 1000)
    public void generateNoiseLevel() {
        String[] locations = {
            "Nightclub-Downtown", "Nightclub-West", "Construction-Site-A",
            "Construction-Site-B", "Stadium", "Concert-Hall", "Airport-Area"
        };
        
        for (String city : cities) {
            for (String location : locations) {
                double baseNoise = location.contains("Nightclub") ? 80 :
                                 location.contains("Construction") ? 75 : 60;
                
                double noiseLevel = baseNoise + (random.nextDouble() - 0.5) * 16;
                noiseLevel = Math.max(40, Math.min(100, noiseLevel));
                
                SensorReading reading = new SensorReading(city, "noise-level", location, noiseLevel);
                kafkaProducerService.sendSensorData("noise-level", city, reading);
            }
        }
        log.info("Sent noise level readings for {} cities, {} locations each", cities.length, locations.length);
    }
    
    /**
     * Generate air quality, temperature, humidity data every 10 seconds
     */
    @Scheduled(fixedRate = 10000)
    public void generateAirTempData() {
        for (String city : cities) {
            // Air quality (AQI: 0-500, typically 50-150)
            double aqi = 50 + random.nextDouble() * 100;
            if (random.nextDouble() < 0.05) { // 5% chance of poor quality
                aqi = 150 + random.nextDouble() * 100;
            }
            SensorReading aqiReading = new SensorReading(city, "air-quality", "City-Center", aqi);
            kafkaProducerService.sendSensorData("air-temp", city, aqiReading);
            
            // Temperature (Gradual drift)
            double currentTemp = cityTemperatures.computeIfAbsent(city, k -> 15.0 + random.nextDouble() * 15); // Base 15-30C
            
            // Drift by max +/- 0.5C
            currentTemp += (random.nextDouble() - 0.5);
            
            // Occasional stronger trend (1% chance)
            if (random.nextDouble() < 0.01) {
                 currentTemp += (random.nextDouble() - 0.5) * 2.0;
            }
            
            // Clamp (-20 to 50)
            currentTemp = Math.max(-20, Math.min(50, currentTemp));
            cityTemperatures.put(city, currentTemp);

            SensorReading tempReading = new SensorReading(city, "temperature", "City-Center", currentTemp);
            kafkaProducerService.sendSensorData("air-temp", city, tempReading);
            
            // Humidity (20-90%)
            double humidity = 40 + random.nextDouble() * 40;
            SensorReading humidityReading = new SensorReading(city, "humidity", "City-Center", humidity);
            kafkaProducerService.sendSensorData("air-temp", city, humidityReading);
        }
        log.info("Sent air/temp/humidity readings for {} cities", cities.length);
    }
}
