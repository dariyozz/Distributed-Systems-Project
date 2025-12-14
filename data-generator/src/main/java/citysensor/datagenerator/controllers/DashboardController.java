package citysensor.datagenerator.controllers;

import citysensor.datagenerator.services.DatabaseService;
import citysensor.datagenerator.services.KafkaConsumerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final KafkaConsumerService kafkaConsumerService;
    private final DatabaseService databaseService;

    /**
     * SSE endpoint for streaming real-time sensor data
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamSensorData() {
        log.info("New SSE client connected");

        return kafkaConsumerService.getSensorDataStream()
                .map(data -> ServerSentEvent.<String>builder()
                        .data(data)
                        .build())
                .doOnSubscribe(sub -> log.info("Client subscribed to sensor stream"))
                .doOnCancel(() -> log.info("Client disconnected from sensor stream"))
                .doOnError(error -> log.error("Error in sensor stream: {}", error.getMessage()));
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public Mono<Map<String, Object>> healthCheck() {
        return Mono.just(Map.of(
                "status", "healthy",
                "service", "dashboard-server",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Get recent alerts from database
     */
    @GetMapping("/alerts/recent")
    public Flux<Object> getRecentAlerts(@RequestParam(defaultValue = "24") int hours,
                                        @RequestParam(defaultValue = "50") int limit) {
        return databaseService.getRecentAlerts(hours, limit)
                .map(alert -> alert);
    }

    /**
     * Get unacknowledged critical alerts
     */
    @GetMapping("/alerts/critical")
    public Flux<Object> getCriticalAlerts() {
        return databaseService.getUnacknowledgedCriticalAlerts()
                .map(alert -> alert);
    }

    /**
     * Get alerts for a specific city
     */
    @GetMapping("/alerts/city/{city}")
    public Flux<Object> getAlertsByCity(@PathVariable String city) {
        return databaseService.getAlertsByCity(city)
                .map(alert -> alert);
    }

    /**
     * Acknowledge an alert
     */
    @PostMapping("/alerts/{id}/acknowledge")
    public Mono<Object> acknowledgeAlert(@PathVariable Long id) {
        return databaseService.acknowledgeAlert(id)
                .map(alert -> alert);
    }

    /**
     * Get recent statistics for a city
     */
    @GetMapping("/statistics/{city}")
    public Flux<Object> getStatistics(@PathVariable String city,
                                      @RequestParam(defaultValue = "10") int limit) {
        return databaseService.getRecentStatistics(city, limit)
                .map(stats -> stats);
    }
}
