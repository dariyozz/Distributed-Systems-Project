package citysensor.datagenerator.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class SensorReading {
    @JsonProperty("city")
    private String city;

    @JsonProperty("sensor_type")
    private String sensorType;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("location")
    private String location;

    @JsonProperty("value")
    private double value;

    public SensorReading(String city, String sensorType, String location, double value) {
        this.city = city;
        this.sensorType = sensorType;
        this.location = location;
        this.value = value;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getSensorType() { return sensorType; }
    public void setSensorType(String sensorType) { this.sensorType = sensorType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
}