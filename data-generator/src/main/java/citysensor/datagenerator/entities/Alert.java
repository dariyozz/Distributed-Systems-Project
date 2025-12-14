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
@Table("alerts")
public class Alert {
    @Id
    private Long id;
    
    private String alertType;
    private String severity;
    private String city;
    private String location;
    private Double value;
    private Double threshold;
    private String message;
    private LocalDateTime timestamp;
    private Boolean acknowledged;
    
    public Alert(String alertType, String severity, String city, String location, 
                 Double value, Double threshold, String message) {
        this.alertType = alertType;
        this.severity = severity;
        this.city = city;
        this.location = location;
        this.value = value;
        this.threshold = threshold;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.acknowledged = false;
    }
}
