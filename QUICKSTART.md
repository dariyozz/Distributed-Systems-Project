# 🚀 Quick Start Guide

## Option 1: Docker (Easiest - Recommended)

```bash
# Start everything
docker-compose up --build -d

# Wait ~15 seconds, then access:
# Dashboard: http://localhost:3000
# Kafka UI: http://localhost:8080
# Flink UI: http://localhost:8081

# View logs
docker-compose logs -f

# Stop everything
docker-compose down
```

## Option 2: Local Development

### Step 1: Start Infrastructure
```bash
docker-compose up -d zookeeper kafka flink-jobmanager flink-taskmanager kafka-ui
```

### Step 2: Build Projects
```bash
# Terminal 1
cd data-generator
mvn clean package -DskipTests
```

```bash
# Terminal 2
cd flink-processor
mvn clean package -DskipTests
```

### Step 3: Start Services

```bash
# Terminal 1 - Data Generator
cd data-generator
java -jar target/data-generator-0.0.1-SNAPSHOT.jar
```

```bash
# Terminal 2 - Dashboard Server (SSE Bridge)
cd data-generator
java -cp target/data-generator-0.0.1-SNAPSHOT.jar citysensor.datagenerator.consumer.DashboardServer
```

```bash
# Terminal 3 - Flink Processor
cd flink-processor
java -jar target/flink-processor-0.0.1-SNAPSHOT.jar
```

```bash
# Terminal 4 - React Dashboard
cd city-dashboard
npm install
npm run dev
```

Access dashboard at: http://localhost:5173

## 🎯 What You'll See

1. **Interactive Map** showing 5 US cities
2. **Real-time sensor data** updating every 1-10 seconds
3. **Live alerts** panel showing critical events
4. **City details** with charts when you click a city
5. **Color-coded markers**: 🟢 Safe | 🟡 Warning | 🔴 Critical

## 🔍 Verify It's Working

### Check Data Flow
```bash
# See raw sensor data in Kafka
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic smoke-fire \
  --from-beginning

# Check dashboard server health
curl http://localhost:8888/health
```

### Check Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f data-generator
```

## 🐛 Troubleshooting

### Dashboard shows "No data"
1. Check dashboard server is running: `curl http://localhost:8888/health`
2. Check browser console for errors (F12)
3. Verify data generator is running and producing data

### "Connection refused" errors
1. Wait 10-15 seconds after starting Kafka
2. Check all services are running: `docker-compose ps`
3. Restart services: `docker-compose restart`

### Port already in use
```bash
# Find what's using the port
netstat -ano | findstr :9092  # Windows
lsof -i :9092                 # Mac/Linux

# Change port in docker-compose.yml if needed
```

## 📚 More Information

- Full documentation: See `README.md`
- All changes made: See `CHANGES.md`
- Configuration options: See `.env.example`

## 🎉 That's It!

You now have a fully functional distributed real-time monitoring system running!
