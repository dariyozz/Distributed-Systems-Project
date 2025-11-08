# City Sensor Monitoring System

A real-time distributed system for monitoring urban sensor data across multiple cities using Apache Kafka, Apache Flink, and React.

## 🏗️ Architecture

```
┌─────────────────┐     ┌──────────┐     ┌─────────────────┐     ┌──────────────┐
│  Data Generator │ ──> │  Kafka   │ ──> │ Flink Processor │ ──> │ Dashboard UI │
│   (Spring Boot) │     │ Topics   │     │  (Stream Jobs)  │     │   (React)    │
└─────────────────┘     └──────────┘     └─────────────────┘     └──────────────┘
                             │                     │
                             │                     ↓
                             │            ┌─────────────────┐
                             └──────────> │ Dashboard Server│
                                          │  (SSE Bridge)   │
                                          └─────────────────┘
```

### Components

1. **Data Generator** - Simulates sensor data for 5 cities:
   - Smoke/Fire sensors (1s interval)
   - Vehicle speed sensors (variable interval)
   - Noise level sensors (1s interval)
   - Air quality, temperature, humidity (10s interval)

2. **Apache Kafka** - Message broker for sensor data streams
   - Topics: `smoke-fire`, `vehicle-speed`, `noise-level`, `air-temp`, `alerts`

3. **Flink Processor** - Real-time stream processing
   - Alert detection based on thresholds
   - Publishes critical events to alerts topic

4. **Dashboard Server** - SSE bridge for frontend
   - Consumes Kafka topics
   - Streams data to React frontend via Server-Sent Events

5. **City Dashboard** - React visualization
   - Interactive map with Leaflet
   - Real-time charts with Recharts
   - Alert management system

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 17+ (for local development)
- Node.js 20+ (for frontend development)
- Maven 3.9+ (for building Java projects)

### Running with Docker (Recommended)

1. **Clone the repository**
```bash
git clone <repository-url>
cd distribuirani_sistemi
```

2. **Start all services**
```bash
docker-compose up --build
```

3. **Access the applications**
   - **Dashboard UI**: http://localhost:3000
   - **Kafka UI**: http://localhost:8080
   - **Flink Dashboard**: http://localhost:8081
   - **Dashboard Server Health**: http://localhost:8888/health

### Running Locally (Development)

#### 1. Start Infrastructure
```bash
docker-compose up zookeeper kafka flink-jobmanager flink-taskmanager kafka-ui
```

#### 2. Run Data Generator
```bash
cd data-generator
mvn clean package
java -jar target/data-generator-0.0.1-SNAPSHOT.jar
```

#### 3. Run Dashboard Server
```bash
cd data-generator
java -cp target/data-generator-0.0.1-SNAPSHOT.jar citysensor.datagenerator.consumer.DashboardServer
```

#### 4. Run Flink Processor
```bash
cd flink-processor
mvn clean package
java -jar target/flink-processor-0.0.1-SNAPSHOT.jar
```

#### 5. Run React Dashboard
```bash
cd city-dashboard
npm install
npm run dev
```
Access at: http://localhost:5173

## ⚙️ Configuration

### Environment Variables

All services support configuration via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | `localhost:9092` | Kafka bootstrap servers |
| `ALLOWED_ORIGIN` | `http://localhost:5173` | CORS allowed origin for Dashboard Server |

### Docker Compose

Modify `docker-compose.yml` to adjust:
- Service ports
- Resource limits
- Environment variables
- Network configuration

### Application Configuration

Each service has an `application.yml` for detailed configuration:

- **data-generator**: `data-generator/src/main/resources/application.yml`
- **flink-processor**: `flink-processor/src/main/resources/application.yml`

## 📊 Monitored Cities

1. **New York** - Population: 8.3M
2. **Los Angeles** - Population: 4.0M
3. **Chicago** - Population: 2.7M
4. **Houston** - Population: 2.3M
5. **Phoenix** - Population: 1.7M

## 🚨 Alert Thresholds

| Sensor Type | Warning | Critical |
|-------------|---------|----------|
| Smoke/Fire | - | 70+ |
| Vehicle Speed | 100+ km/h | 130+ km/h |
| Noise Level | 90+ dB | 100+ dB |
| Air Quality (AQI) | 150+ | 200+ |
| Temperature | < 0°C or > 38°C | - |

## 🛠️ Development

### Building Projects

```bash
# Build data-generator
cd data-generator
mvn clean package

# Build flink-processor
cd flink-processor
mvn clean package

# Build React dashboard
cd city-dashboard
npm run build
```

### Project Structure

```
distribuirani_sistemi/
├── data-generator/          # Spring Boot sensor data generator
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── citysensor/
│   │       │       ├── generators/    # Sensor simulators
│   │       │       ├── consumer/      # Dashboard Server (SSE)
│   │       │       └── models/        # Data models
│   │       └── resources/
│   │           └── application.yml
│   ├── Dockerfile
│   └── pom.xml
├── flink-processor/         # Flink stream processing
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── citysensor/
│   │       │       └── alerts/        # Alert detection logic
│   │       └── resources/
│   │           └── application.yml
│   ├── Dockerfile
│   └── pom.xml
├── city-dashboard/          # React frontend
│   ├── src/
│   │   ├── App.jsx           # Main application
│   │   └── main.jsx
│   ├── Dockerfile
│   └── package.json
└── docker-compose.yml       # Complete stack orchestration
```

## 🔧 Troubleshooting

### Kafka Connection Issues

If services can't connect to Kafka:
```bash
# Check Kafka is running
docker-compose ps kafka

# View Kafka logs
docker-compose logs kafka

# Verify topic creation
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Dashboard Not Receiving Data

1. Check Dashboard Server is running on port 8888
2. Verify CORS origin matches your frontend URL
3. Check browser console for SSE connection errors
4. Verify Kafka topics have data:
```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic smoke-fire \
  --from-beginning
```

### Flink Job Not Starting

1. Check Flink JobManager is running: http://localhost:8081
2. View Flink logs:
```bash
docker-compose logs flink-processor-app
```
3. Ensure TaskManager has available slots

## 📝 API Endpoints

### Dashboard Server

- **GET /stream** - SSE endpoint for real-time data
- **GET /health** - Health check endpoint

### Kafka UI

- Access at http://localhost:8080 to view:
  - Topics and messages
  - Consumer groups
  - Brokers status

### Flink Dashboard

- Access at http://localhost:8081 to view:
  - Running jobs
  - Task managers
  - Job metrics

## 🐳 Docker Commands

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f [service-name]

# Stop all services
docker-compose down

# Remove volumes (clean start)
docker-compose down -v

# Rebuild specific service
docker-compose up -d --build [service-name]
```

## 📈 Future Enhancements

- [ ] Add data persistence (MongoDB/Cassandra)
- [ ] Implement historical data analysis
- [ ] Add Prometheus + Grafana monitoring
- [ ] Implement authentication/authorization
- [ ] Add Kubernetes deployment manifests
- [ ] Enhance alert notification system
- [ ] Add machine learning predictions

## 📄 License

This project is for educational purposes.

## 👥 Contributors

- Your Name

## 🙏 Acknowledgments

- Apache Kafka
- Apache Flink
- React & Vite
- Spring Boot
