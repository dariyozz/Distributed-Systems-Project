#!/bin/bash

echo "🚀 Starting City Sensor Monitoring System (Local Development)"
echo "=============================================================="

# Start infrastructure
echo "📦 Starting Kafka, Zookeeper, and Flink..."
docker-compose up -d zookeeper kafka flink-jobmanager flink-taskmanager kafka-ui

echo "⏳ Waiting for Kafka to be ready..."
sleep 10

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Please install Maven 3.9+ to continue."
    exit 1
fi

# Build projects
echo "🔨 Building Java projects..."
cd data-generator && mvn clean package -DskipTests && cd ..
cd flink-processor && mvn clean package -DskipTests && cd ..

echo ""
echo "✅ Infrastructure is ready!"
echo ""
echo "To start the applications:"
echo ""
echo "Terminal 1 - Data Generator:"
echo "  cd data-generator"
echo "  java -jar target/data-generator-0.0.1-SNAPSHOT.jar"
echo ""
echo "Terminal 2 - Dashboard Server:"
echo "  cd data-generator"
echo "  java -cp target/data-generator-0.0.1-SNAPSHOT.jar citysensor.datagenerator.consumer.DashboardServer"
echo ""
echo "Terminal 3 - Flink Processor:"
echo "  cd flink-processor"
echo "  java -jar target/flink-processor-0.0.1-SNAPSHOT.jar"
echo ""
echo "Terminal 4 - React Dashboard:"
echo "  cd city-dashboard"
echo "  npm install && npm run dev"
echo ""
echo "📊 Access points:"
echo "  - Dashboard: http://localhost:5173"
echo "  - Kafka UI: http://localhost:8080"
echo "  - Flink UI: http://localhost:8081"
