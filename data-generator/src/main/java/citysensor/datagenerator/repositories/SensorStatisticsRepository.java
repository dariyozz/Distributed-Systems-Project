package citysensor.datagenerator.repositories;

import citysensor.datagenerator.entities.SensorStatistics;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface SensorStatisticsRepository extends ReactiveCrudRepository<SensorStatistics, Long> {
    
    Flux<SensorStatistics> findByCityAndSensorType(String city, String sensorType);
    
    @Query("SELECT * FROM sensor_statistics WHERE city = :city ORDER BY period_end DESC LIMIT :limit")
    Flux<SensorStatistics> findRecentByCityLimit(String city, int limit);
    
    @Query("SELECT * FROM sensor_statistics WHERE sensor_type = :sensorType AND period_end >= :since")
    Flux<SensorStatistics> findBySensorTypeSince(String sensorType, LocalDateTime since);
    
    Mono<SensorStatistics> findTopByCityAndSensorTypeOrderByPeriodEndDesc(String city, String sensorType);
}
