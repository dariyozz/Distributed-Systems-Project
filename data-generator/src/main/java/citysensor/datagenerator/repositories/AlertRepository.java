package citysensor.datagenerator.repositories;

import citysensor.datagenerator.entities.Alert;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@Repository
public interface AlertRepository extends ReactiveCrudRepository<Alert, Long> {
    
    Flux<Alert> findByCity(String city);
    
    Flux<Alert> findBySeverity(String severity);
    
    @Query("SELECT * FROM alerts WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    Flux<Alert> findRecentAlerts(LocalDateTime since, int limit);
    
    @Query("SELECT * FROM alerts WHERE severity = 'CRITICAL' AND acknowledged = false ORDER BY timestamp DESC")
    Flux<Alert> findUnacknowledgedCriticalAlerts();
    
    Flux<Alert> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}
