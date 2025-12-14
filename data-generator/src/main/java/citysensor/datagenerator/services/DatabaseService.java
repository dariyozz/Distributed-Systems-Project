package citysensor.datagenerator.services;

import citysensor.datagenerator.entities.Alert;
import citysensor.datagenerator.entities.SensorStatistics;
import citysensor.datagenerator.repositories.AlertRepository;
import citysensor.datagenerator.repositories.SensorStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseService {
    
    private final AlertRepository alertRepository;
    private final SensorStatisticsRepository statisticsRepository;
    
    /**
     * Save critical alert to database
     */
    public Mono<Alert> saveAlert(String alertType, String severity, String city, 
                                  String location, Double value, Double threshold, String message) {
        Alert alert = new Alert(alertType, severity, city, location, value, threshold, message);
        
        return alertRepository.save(alert)
            .doOnSuccess(saved -> log.info("Saved alert: {} in {} - {}", severity, city, alertType))
            .doOnError(error -> log.error("Failed to save alert: {}", error.getMessage()));
    }
    
    /**
     * Save sensor statistics
     */
    public Mono<SensorStatistics> saveStatistics(String city, String sensorType, Double avgValue,
                                                  Double minValue, Double maxValue, Integer readingsCount,
                                                  LocalDateTime periodStart, LocalDateTime periodEnd) {
        SensorStatistics stats = new SensorStatistics(city, sensorType, avgValue, minValue, 
                                                       maxValue, readingsCount, periodStart, periodEnd);
        
        return statisticsRepository.save(stats)
            .doOnSuccess(saved -> log.debug("Saved statistics: {} - {} (avg: {})", city, sensorType, avgValue))
            .doOnError(error -> log.error("Failed to save statistics: {}", error.getMessage()));
    }
    
    /**
     * Get recent alerts
     */
    public Flux<Alert> getRecentAlerts(int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return alertRepository.findRecentAlerts(since, limit);
    }
    
    /**
     * Get unacknowledged critical alerts
     */
    public Flux<Alert> getUnacknowledgedCriticalAlerts() {
        return alertRepository.findUnacknowledgedCriticalAlerts();
    }
    
    /**
     * Get alerts for specific city
     */
    public Flux<Alert> getAlertsByCity(String city) {
        return alertRepository.findByCity(city);
    }
    
    /**
     * Get recent statistics for a city
     */
    public Flux<SensorStatistics> getRecentStatistics(String city, int limit) {
        return statisticsRepository.findRecentByCityLimit(city, limit);
    }
    
    /**
     * Acknowledge an alert
     */
    public Mono<Alert> acknowledgeAlert(Long alertId) {
        return alertRepository.findById(alertId)
            .flatMap(alert -> {
                alert.setAcknowledged(true);
                return alertRepository.save(alert);
            })
            .doOnSuccess(alert -> log.info("Alert {} acknowledged", alertId));
    }
}
