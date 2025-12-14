-- City Sensor Monitoring System Database Schema

-- Alerts table
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    city VARCHAR(100) NOT NULL,
    location VARCHAR(200) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    threshold DOUBLE PRECISION NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    acknowledged BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for faster queries
CREATE INDEX IF NOT EXISTS idx_alerts_city ON alerts(city);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_timestamp ON alerts(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_acknowledged ON alerts(acknowledged) WHERE acknowledged = FALSE;

-- Sensor statistics table
CREATE TABLE IF NOT EXISTS sensor_statistics (
    id BIGSERIAL PRIMARY KEY,
    city VARCHAR(100) NOT NULL,
    sensor_type VARCHAR(50) NOT NULL,
    avg_value DOUBLE PRECISION NOT NULL,
    min_value DOUBLE PRECISION NOT NULL,
    max_value DOUBLE PRECISION NOT NULL,
    readings_count INTEGER NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for statistics
CREATE INDEX IF NOT EXISTS idx_stats_city_type ON sensor_statistics(city, sensor_type);
CREATE INDEX IF NOT EXISTS idx_stats_period_end ON sensor_statistics(period_end DESC);
CREATE INDEX IF NOT EXISTS idx_stats_sensor_type ON sensor_statistics(sensor_type);

-- Optional: Create a view for recent critical alerts
CREATE OR REPLACE VIEW recent_critical_alerts AS
SELECT 
    id,
    alert_type,
    severity,
    city,
    location,
    value,
    threshold,
    message,
    timestamp,
    acknowledged
FROM alerts
WHERE severity = 'CRITICAL'
  AND acknowledged = FALSE
  AND timestamp >= NOW() - INTERVAL '24 hours'
ORDER BY timestamp DESC;

-- Optional: Create a view for city statistics summary
CREATE OR REPLACE VIEW city_statistics_summary AS
SELECT 
    city,
    sensor_type,
    avg_value,
    min_value,
    max_value,
    period_end as last_updated
FROM sensor_statistics ss1
WHERE period_end = (
    SELECT MAX(period_end) 
    FROM sensor_statistics ss2 
    WHERE ss1.city = ss2.city 
      AND ss1.sensor_type = ss2.sensor_type
);
