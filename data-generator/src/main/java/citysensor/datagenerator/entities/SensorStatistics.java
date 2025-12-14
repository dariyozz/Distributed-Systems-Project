package citysensor.datagenerator.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("sensor_statistics")
public class SensorStatistics {
    @Id
    private Long id;
    
    private String city;
    private String sensorType;
    private Double avgValue;
    private Double minValue;
    private Double maxValue;
    private Integer readingsCount;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime createdAt;
    
    public SensorStatistics(String city, String sensorType, Double avgValue, 
                           Double minValue, Double maxValue, Integer readingsCount,
                           LocalDateTime periodStart, LocalDateTime periodEnd) {
        this.city = city;
        this.sensorType = sensorType;
        this.avgValue = avgValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.readingsCount = readingsCount;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.createdAt = LocalDateTime.now();
    }
}
