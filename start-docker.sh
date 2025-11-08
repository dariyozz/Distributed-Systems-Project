#!/bin/bash

echo "🐳 Starting City Sensor Monitoring System (Docker)"
echo "=================================================="

# Stop any existing containers
echo "🛑 Stopping existing containers..."
docker-compose down

# Build and start all services
echo "🔨 Building and starting all services..."
docker-compose up --build -d

echo "⏳ Waiting for services to be ready..."
sleep 15

echo ""
echo "✅ All services started!"
echo ""
echo "📊 Access points:"
echo "  - Dashboard: http://localhost:3000"
echo "  - Kafka UI: http://localhost:8080"
echo "  - Flink UI: http://localhost:8081"
echo "  - Dashboard Server Health: http://localhost:8888/health"
echo ""
echo "📝 To view logs:"
echo "  docker-compose logs -f [service-name]"
echo ""
echo "🛑 To stop all services:"
echo "  docker-compose down"
