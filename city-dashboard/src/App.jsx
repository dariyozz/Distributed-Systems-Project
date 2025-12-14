import React, { useState, useEffect } from 'react';
import { MapContainer, TileLayer, CircleMarker, Popup, Tooltip, useMap } from 'react-leaflet';
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip as ChartTooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import { AlertTriangle, Flame, Car, Volume2, Wind, Thermometer, Activity, X, TrendingUp, TrendingDown, MapPin } from 'lucide-react';
import 'leaflet/dist/leaflet.css';

// City coordinates and metadata
const CITIES = {
    'New York': {
        lat: 40.7128,
        lng: -74.0060,
        color: '#3b82f6',
        buildings: 50,
        roads: 20,
        population: '8.3M'
    },
    'Los Angeles': {
        lat: 34.0522,
        lng: -118.2437,
        color: '#8b5cf6',
        buildings: 45,
        roads: 25,
        population: '4.0M'
    },
    'Chicago': {
        lat: 41.8781,
        lng: -87.6298,
        color: '#10b981',
        buildings: 40,
        roads: 18,
        population: '2.7M'
    },
    'Houston': {
        lat: 29.7604,
        lng: -95.3698,
        color: '#f59e0b',
        buildings: 35,
        roads: 22,
        population: '2.3M'
    },
    'Phoenix': {
        lat: 33.4484,
        lng: -112.0740,
        color: '#ef4444',
        buildings: 30,
        roads: 15,
        population: '1.7M'
    }
};

const ALERT_COLORS = {
    CRITICAL: '#dc2626',
    WARNING: '#f59e0b',
    SAFE: '#10b981'
};

// Map component to auto-fit bounds
function MapBounds() {
    const map = useMap();
    useEffect(() => {
        const bounds = Object.values(CITIES).map(c => [c.lat, c.lng]);
        map.fitBounds(bounds, { padding: [50, 50] });
    }, [map]);
    return null;
}

export default function App() {
    const [selectedCity, setSelectedCity] = useState(null);
    const [cityData, setCityData] = useState({});
    const [alerts, setAlerts] = useState([]);
    const [timeSeriesData, setTimeSeriesData] = useState({});
    const [globalStats, setGlobalStats] = useState({
        totalAlerts: 0,
        criticalAlerts: 0,
        avgAirQuality: 0,
        avgTemperature: 0
    });

    useEffect(() => {
        // Initialize city data structure
        const initialData = {};

        Object.keys(CITIES).forEach(city => {
            initialData[city] = {
                smoke: { current: 0, trend: [], status: 'SAFE', affectedBuildings: 0, readings: {} },
                speed: { current: 0, trend: [], violations: 0, avgSpeed: 0 },
                noise: { current: 0, trend: [], violations: 0, locations: {}, hotspots: [] },
                airQuality: { current: 50, trend: [], level: 'Good' },
                temperature: { current: 20, humidity: 60, trend: [] },
                lastUpdate: Date.now()
            };
        });

        setCityData(initialData);

        // Fetch initial data
        const fetchInitialData = async () => {
            try {
                const alertsRes = await fetch('http://localhost:8888/api/alerts/recent');
                const alertsData = await alertsRes.json();
                alertsData.forEach(processAlert);
            } catch (error) {
                console.error('Error fetching initial data:', error);
            }
        };

        fetchInitialData();

        // Track connection state
        let eventSource = null;
        let reconnectTimeout = null;
        let isMounted = true;
        let connectionStartTime = 0;
        const MAX_RECONNECT_DELAY = 15000; // Max 15 seconds delay
        let reconnectDelay = 1000;

        const connectSSE = () => {
            if (!isMounted || eventSource) return;

            // Add timestamp to prevent caching
            const url = new URL('http://localhost:8888/api/stream');
            url.searchParams.append('t', Date.now().toString());

            const newEventSource = new EventSource(url.toString());
            eventSource = newEventSource;
            connectionStartTime = Date.now();

            newEventSource.onopen = (e) => {
                if (e.target !== eventSource) return;
                console.log('✅ Connected to Java Kafka bridge');
                // We don't reset delay here immediately to handle flapping
            };

            newEventSource.onmessage = (event) => {
                if (event.target !== eventSource) return;
                try {
                    const message = JSON.parse(event.data);

                    if (message.type === 'sensor-data') {
                        processSensorData(message.data, message.topic);
                    } else if (message.type === 'alert') {
                        processAlert(message.data);
                    }
                } catch (error) {
                    console.error('Error parsing SSE message:', error);
                }
            };

            newEventSource.onerror = (event) => {
                if (event.target !== eventSource) return;
                console.error('❌ SSE connection error:', event);

                newEventSource.close();
                if (eventSource === newEventSource) {
                    eventSource = null;
                }

                if (!isMounted) return;

                const connectionDuration = Date.now() - connectionStartTime;

                // Flapping detection: If connection lasted less than 5 seconds, back off aggressively
                if (connectionDuration < 5000) {
                    reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
                    console.log(`⚠️ Connection unstable (lasted ${connectionDuration}ms). Increasing delay to ${reconnectDelay}ms`);
                } else {
                    // If connection was stable for a while, reset delay
                    reconnectDelay = 1000;
                }

                console.log(`🔄 Reconnecting in ${reconnectDelay}ms...`);
                reconnectTimeout = setTimeout(() => {
                    if (isMounted) connectSSE();
                }, reconnectDelay);
            };
        };

        // Initial connection with 2s delay to allow server to cleanup previous connection
        const initialTimer = setTimeout(connectSSE, 2000);

        const cleanup = () => {
            console.log('🔌 Cleaning up SSE connection...');
            isMounted = false;
            clearTimeout(initialTimer);
            if (reconnectTimeout) clearTimeout(reconnectTimeout);
            if (eventSource) {
                eventSource.close();
                eventSource = null;
            }
        };

        // Ensure cleanup runs on page refresh/close
        window.addEventListener('beforeunload', cleanup);

        // Cleanup on component unmount
        return () => {
            window.removeEventListener('beforeunload', cleanup);
            cleanup();
        };
    }, []);

    const acknowledgeAlert = async (alertId, e) => {
        e.stopPropagation();
        try {
            await fetch(`http://localhost:8888/api/alerts/${alertId}/acknowledge`, {
                method: 'POST'
            });
            setAlerts(prev => prev.map(a =>
                a.id === alertId ? { ...a, acknowledged: true } : a
            ));
        } catch (error) {
            console.error('Error acknowledging alert:', error);
        }
    };

    // ADD these processing functions (same as before):

    const processSensorData = (data, topic) => {
        if (!data || !data.city) return;

        const city = data.city;
        const sensorType = data.sensor_type;
        const value = data.value;
        const location = data.location;

        setCityData(prev => {
            const newData = { ...prev };

            if (!newData[city]) {
                console.warn(`Unknown city: ${city}`);
                return prev;
            }

            const cityInfo = { ...newData[city] };

            switch (sensorType) {
                case 'smoke-fire':
                    processSmokeData(cityInfo, value, location, city);
                    break;
                case 'vehicle-speed':
                    processSpeedData(cityInfo, value, location, city);
                    break;
                case 'noise-level':
                    processNoiseData(cityInfo, value, location, city);
                    break;
                case 'air-quality':
                    processAirQualityData(cityInfo, value, city);
                    break;
                case 'temperature':
                    processTemperatureData(cityInfo, value, city);
                    break;
                case 'humidity':
                    processHumidityData(cityInfo, value, city);
                    break;
            }

            cityInfo.lastUpdate = Date.now();
            newData[city] = cityInfo;
            return newData;
        });

        updateGlobalStats();
    };

    const processSmokeData = (cityInfo, value, location, city) => {
        if (!cityInfo.smoke.readings) cityInfo.smoke.readings = {};
        cityInfo.smoke.readings[location] = value;

        const readings = Object.values(cityInfo.smoke.readings);
        cityInfo.smoke.current = readings.reduce((a, b) => a + b, 0) / readings.length;

        cityInfo.smoke.trend.push(cityInfo.smoke.current);
        if (cityInfo.smoke.trend.length > 20) cityInfo.smoke.trend.shift();

        if (cityInfo.smoke.current > 70) {
            cityInfo.smoke.status = 'CRITICAL';
            cityInfo.smoke.affectedBuildings = readings.filter(r => r > 70).length;

            if (value > 70) {
                generateAlert('CRITICAL', 'Fire Risk', city,
                    `High smoke level (${value.toFixed(1)}) at ${location}`);
            }
        } else if (cityInfo.smoke.current > 50) {
            cityInfo.smoke.status = 'WARNING';
            cityInfo.smoke.affectedBuildings = readings.filter(r => r > 50).length;
        } else {
            cityInfo.smoke.status = 'SAFE';
            cityInfo.smoke.affectedBuildings = 0;
        }
    };

    const processSpeedData = (cityInfo, value, location, city) => {
        cityInfo.speed.current = value;
        cityInfo.speed.trend.push(value);
        if (cityInfo.speed.trend.length > 20) cityInfo.speed.trend.shift();

        cityInfo.speed.avgSpeed = cityInfo.speed.trend.reduce((a, b) => a + b, 0) / cityInfo.speed.trend.length;

        if (value > 100) {
            cityInfo.speed.violations = (cityInfo.speed.violations || 0) + 1;

            if (value > 120) {
                generateAlert('CRITICAL', 'Extreme Speeding', city,
                    `Vehicle at ${value.toFixed(0)} km/h on ${location}`);
            }
        }
    };

    const processNoiseData = (cityInfo, value, location, city) => {
        if (!cityInfo.noise.locations) cityInfo.noise.locations = {};
        cityInfo.noise.locations[location] = value;

        const noiseValues = Object.values(cityInfo.noise.locations);
        cityInfo.noise.current = noiseValues.reduce((a, b) => a + b, 0) / noiseValues.length;

        cityInfo.noise.trend.push(cityInfo.noise.current);
        if (cityInfo.noise.trend.length > 20) cityInfo.noise.trend.shift();

        if (value > 95) {
            cityInfo.noise.violations = (cityInfo.noise.violations || 0) + 1;
            generateAlert('WARNING', 'Noise Pollution', city,
                `Excessive noise (${value.toFixed(0)} dB) at ${location}`);
        }

        cityInfo.noise.hotspots = Object.entries(cityInfo.noise.locations)
            .filter(([loc, val]) => val > 90)
            .map(([loc, val]) => ({ location: loc, level: val }));
    };

    const processAirQualityData = (cityInfo, value, city) => {
        cityInfo.airQuality.current = value;
        cityInfo.airQuality.trend.push(value);
        if (cityInfo.airQuality.trend.length > 20) cityInfo.airQuality.trend.shift();

        if (value <= 50) cityInfo.airQuality.level = 'Good';
        else if (value <= 100) cityInfo.airQuality.level = 'Moderate';
        else if (value <= 150) cityInfo.airQuality.level = 'Unhealthy for Sensitive';
        else {
            cityInfo.airQuality.level = 'Unhealthy';
            if (value > 150) {
                generateAlert('CRITICAL', 'Poor Air Quality', city,
                    `AQI: ${value.toFixed(0)} - Unhealthy`);
            }
        }
    };

    const processTemperatureData = (cityInfo, value, city) => {
        cityInfo.temperature.current = value;
        cityInfo.temperature.trend.push(value);
        if (cityInfo.temperature.trend.length > 20) cityInfo.temperature.trend.shift();

        if (value > 38 || value < 0) {
            generateAlert('WARNING', 'Extreme Temperature', city,
                `Temperature: ${value.toFixed(1)}°C`);
        }
    };

    const processHumidityData = (cityInfo, value, city) => {
        cityInfo.temperature.humidity = value;
    };

    const processAlert = (alertData) => {
        const alert = {
            id: Date.now() + Math.random(),
            severity: alertData.severity || 'WARNING',
            type: alertData.alert_type || 'Alert',
            city: alertData.city,
            message: alertData.message,
            timestamp: new Date(alertData.timestamp),
            read: false
        };

        setAlerts(prev => [alert, ...prev].slice(0, 50));

        setGlobalStats(prev => ({
            ...prev,
            totalAlerts: prev.totalAlerts + 1,
            criticalAlerts: alert.severity === 'CRITICAL' ? prev.criticalAlerts + 1 : prev.criticalAlerts
        }));
    };

    const generateAlert = (severity, type, city, message) => {
        const alert = {
            id: Date.now() + Math.random(),
            severity,
            type,
            city,
            message,
            timestamp: new Date(),
            read: false
        };

        setAlerts(prev => [alert, ...prev].slice(0, 50));

        setGlobalStats(prev => ({
            ...prev,
            totalAlerts: prev.totalAlerts + 1,
            criticalAlerts: severity === 'CRITICAL' ? prev.criticalAlerts + 1 : prev.criticalAlerts
        }));
    };

    const updateGlobalStats = () => {
        setCityData(currentData => {
            const cities = Object.values(currentData);
            if (cities.length === 0) return currentData;

            const avgAqi = cities.reduce((sum, c) => sum + (c.airQuality?.current || 0), 0) / cities.length;
            const avgTemp = cities.reduce((sum, c) => sum + (c.temperature?.current || 0), 0) / cities.length;

            setGlobalStats(prev => ({
                ...prev,
                avgAirQuality: avgAqi,
                avgTemperature: avgTemp
            }));

            return currentData;
        });
    };


    const getCityStatus = (city) => {
        const data = cityData[city];
        if (!data) return 'SAFE';

        if (data.smoke.status === 'CRITICAL' || data.airQuality.current > 150) return 'CRITICAL';
        if (data.smoke.status === 'WARNING' || data.speed.current > 100 || data.noise.current > 90) return 'WARNING';
        return 'SAFE';
    };

    const getMarkerSize = (city) => {
        const status = getCityStatus(city);
        if (status === 'CRITICAL') return 25;
        if (status === 'WARNING') return 20;
        return 15;
    };

    return (
        <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 text-white">
            {/* Header */}
            <div className="bg-slate-800/50 backdrop-blur-lg border-b border-slate-700 sticky top-0 z-50">
                <div className="max-w-7xl mx-auto px-6 py-4">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center space-x-3">
                            <MapPin className="w-8 h-8 text-blue-400" />
                            <div>
                                <h1 className="text-2xl font-bold">City Sensor Network</h1>
                                <p className="text-sm text-slate-400">Real-time Urban Monitoring System</p>
                            </div>
                        </div>
                        <div className="flex items-center space-x-6">
                            <div className="text-center">
                                <div className="text-2xl font-bold text-blue-400">{Object.keys(CITIES).length}</div>
                                <div className="text-xs text-slate-400">Cities</div>
                            </div>
                            <div className="text-center">
                                <div className="text-2xl font-bold text-yellow-400">{globalStats.totalAlerts}</div>
                                <div className="text-xs text-slate-400">Total Alerts</div>
                            </div>
                            <div className="text-center">
                                <div className="text-2xl font-bold text-red-400">{globalStats.criticalAlerts}</div>
                                <div className="text-xs text-slate-400">Critical</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div className="flex h-[calc(100vh-88px)]">
                {/* Main Map */}
                <div className="flex-1 relative">
                    <MapContainer
                        center={[37.0902, -95.7129]}
                        zoom={4}
                        style={{ height: '100%', width: '100%' }}
                        zoomControl={true}
                    >
                        <TileLayer
                            url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
                            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                        />
                        <MapBounds />

                        {Object.entries(CITIES).map(([name, info]) => {
                            const status = getCityStatus(name);
                            const data = cityData[name];

                            return (
                                <CircleMarker
                                    key={name}
                                    center={[info.lat, info.lng]}
                                    radius={getMarkerSize(name)}
                                    pathOptions={{
                                        fillColor: ALERT_COLORS[status],
                                        color: 'white',
                                        weight: 2,
                                        opacity: 1,
                                        fillOpacity: 0.8
                                    }}
                                    eventHandlers={{
                                        click: () => setSelectedCity(name)
                                    }}
                                >
                                    <Tooltip permanent direction="top" offset={[0, -10]}>
                                        <div className="text-center">
                                            <div className="font-bold">{name}</div>
                                            <div className="text-xs">{info.population}</div>
                                        </div>
                                    </Tooltip>
                                    <Popup>
                                        <div className="p-2">
                                            <h3 className="font-bold text-lg mb-2">{name}</h3>
                                            {data && (
                                                <div className="space-y-1 text-sm">
                                                    <div>🔥 Smoke: {data.smoke.current.toFixed(1)}</div>
                                                    <div>🚗 Avg Speed: {data.speed.avgSpeed.toFixed(0)} km/h</div>
                                                    <div>🔊 Noise: {data.noise.current.toFixed(0)} dB</div>
                                                    <div>💨 AQI: {data.airQuality.current.toFixed(0)}</div>
                                                    <div>🌡️ Temp: {data.temperature.current.toFixed(1)}°C</div>
                                                </div>
                                            )}
                                        </div>
                                    </Popup>
                                </CircleMarker>
                            );
                        })}
                    </MapContainer>

                    {/* Floating Legend */}
                    <div className="absolute top-4 left-4 bg-slate-800/90 backdrop-blur-lg rounded-lg p-4 border border-slate-700">
                        <h3 className="font-semibold mb-2">Status Legend</h3>
                        <div className="space-y-2 text-sm">
                            <div className="flex items-center space-x-2">
                                <div className="w-4 h-4 rounded-full" style={{ backgroundColor: ALERT_COLORS.SAFE }}></div>
                                <span>Safe Operations</span>
                            </div>
                            <div className="flex items-center space-x-2">
                                <div className="w-4 h-4 rounded-full" style={{ backgroundColor: ALERT_COLORS.WARNING }}></div>
                                <span>Warning Level</span>
                            </div>
                            <div className="flex items-center space-x-2">
                                <div className="w-4 h-4 rounded-full" style={{ backgroundColor: ALERT_COLORS.CRITICAL }}></div>
                                <span>Critical Alert</span>
                            </div>
                        </div>
                    </div>

                    {/* Global Stats Cards */}
                    <div className="absolute bottom-4 left-4 right-4 flex space-x-4">
                        <div className="flex-1 bg-slate-800/90 backdrop-blur-lg rounded-lg p-4 border border-slate-700">
                            <div className="flex items-center justify-between">
                                <div>
                                    <div className="text-sm text-slate-400">Avg Air Quality</div>
                                    <div className="text-2xl font-bold">{globalStats.avgAirQuality.toFixed(0)} AQI</div>
                                    <div className="text-xs text-slate-500">
                                        {globalStats.avgAirQuality <= 50 ? 'Good' : globalStats.avgAirQuality <= 100 ? 'Moderate' : 'Unhealthy'}
                                    </div>
                                </div>
                                <Wind className="w-10 h-10 text-blue-400 opacity-50" />
                            </div>
                        </div>

                        <div className="flex-1 bg-slate-800/90 backdrop-blur-lg rounded-lg p-4 border border-slate-700">
                            <div className="flex items-center justify-between">
                                <div>
                                    <div className="text-sm text-slate-400">Avg Temperature</div>
                                    <div className="text-2xl font-bold">{globalStats.avgTemperature.toFixed(1)}°C</div>
                                    <div className="text-xs text-slate-500">Across all cities</div>
                                </div>
                                <Thermometer className="w-10 h-10 text-orange-400 opacity-50" />
                            </div>
                        </div>
                    </div>
                </div>

                {/* Right Sidebar - City Details */}
                {selectedCity && (
                    <div className="w-[500px] bg-slate-800/95 backdrop-blur-lg border-l border-slate-700 overflow-y-auto">
                        <div className="sticky top-0 bg-slate-800 border-b border-slate-700 p-4 flex items-center justify-between z-10">
                            <div className="flex items-center space-x-3">
                                <div
                                    className="w-4 h-4 rounded-full"
                                    style={{ backgroundColor: CITIES[selectedCity].color }}
                                ></div>
                                <div>
                                    <h2 className="text-xl font-bold">{selectedCity}</h2>
                                    <p className="text-sm text-slate-400">{CITIES[selectedCity].population} residents</p>
                                </div>
                            </div>
                            <button
                                onClick={() => setSelectedCity(null)}
                                className="p-2 hover:bg-slate-700 rounded-lg transition-colors"
                            >
                                <X className="w-5 h-5" />
                            </button>
                        </div>

                        {cityData[selectedCity] && (
                            <div className="p-4 space-y-6">
                                {/* Fire/Smoke Section */}
                                <div className="bg-slate-700/50 rounded-lg p-4">
                                    <div className="flex items-center justify-between mb-3">
                                        <div className="flex items-center space-x-2">
                                            <Flame className="w-5 h-5 text-orange-400" />
                                            <h3 className="font-semibold">Fire Risk Assessment</h3>
                                        </div>
                                        <span className={`px-3 py-1 rounded-full text-xs font-semibold ${cityData[selectedCity].smoke.status === 'CRITICAL' ? 'bg-red-500' :
                                                cityData[selectedCity].smoke.status === 'WARNING' ? 'bg-yellow-500' :
                                                    'bg-green-500'
                                            }`}>
                                            {cityData[selectedCity].smoke.status}
                                        </span>
                                    </div>

                                    <div className="grid grid-cols-2 gap-4 mb-4">
                                        <div>
                                            <div className="text-sm text-slate-400">Current Level</div>
                                            <div className="text-2xl font-bold">{cityData[selectedCity].smoke.current.toFixed(1)}</div>
                                        </div>
                                        <div>
                                            <div className="text-sm text-slate-400">Buildings at Risk</div>
                                            <div className="text-2xl font-bold text-orange-400">
                                                {cityData[selectedCity].smoke.affectedBuildings}/{CITIES[selectedCity].buildings}
                                            </div>
                                        </div>
                                    </div>

                                    <ResponsiveContainer width="100%" height={100}>
                                        <LineChart data={cityData[selectedCity].smoke.trend.map((v, i) => ({ value: v, index: i }))}>
                                            <Line type="monotone" dataKey="value" stroke="#fb923c" strokeWidth={2} dot={false} />
                                            <CartesianGrid stroke="#334155" strokeDasharray="3 3" />
                                        </LineChart>
                                    </ResponsiveContainer>
                                </div>

                                {/* Vehicle Speed Section */}
                                <div className="bg-slate-700/50 rounded-lg p-4">
                                    <div className="flex items-center space-x-2 mb-3">
                                        <Car className="w-5 h-5 text-blue-400" />
                                        <h3 className="font-semibold">Traffic Analysis</h3>
                                    </div>

                                    <div className="grid grid-cols-3 gap-4 mb-4">
                                        <div>
                                            <div className="text-sm text-slate-400">Current</div>
                                            <div className="text-xl font-bold">{cityData[selectedCity].speed.current.toFixed(0)}</div>
                                            <div className="text-xs text-slate-500">km/h</div>
                                        </div>
                                        <div>
                                            <div className="text-sm text-slate-400">Average</div>
                                            <div className="text-xl font-bold">{cityData[selectedCity].speed.avgSpeed.toFixed(0)}</div>
                                            <div className="text-xs text-slate-500">km/h</div>
                                        </div>
                                        <div>
                                            <div className="text-sm text-slate-400">Violations</div>
                                            <div className="text-xl font-bold text-red-400">{cityData[selectedCity].speed.violations}</div>
                                            <div className="text-xs text-slate-500">today</div>
                                        </div>
                                    </div>

                                    <ResponsiveContainer width="100%" height={100}>
                                        <BarChart data={cityData[selectedCity].speed.trend.slice(-10).map((v, i) => ({
                                            speed: v,
                                            name: `T-${10 - i}`,
                                            fill: v > 100 ? '#ef4444' : v > 80 ? '#f59e0b' : '#10b981'
                                        }))}>
                                            <Bar dataKey="speed" />
                                            <XAxis dataKey="name" stroke="#64748b" />
                                            <YAxis stroke="#64748b" />
                                            <CartesianGrid stroke="#334155" strokeDasharray="3 3" />
                                        </BarChart>
                                    </ResponsiveContainer>

                                    <div className="mt-3 text-xs text-slate-400">
                                        <div className="flex items-center justify-between">
                                            <span>🟢 Safe (&lt;80)</span>
                                            <span>🟡 Fast (80-100)</span>
                                            <span>🔴 Violation (&gt;100)</span>
                                        </div>
                                    </div>
                                </div>

                                {/* Noise Pollution */}
                                <div className="bg-slate-700/50 rounded-lg p-4">
                                    <div className="flex items-center space-x-2 mb-3">
                                        <Volume2 className="w-5 h-5 text-purple-400" />
                                        <h3 className="font-semibold">Noise Pollution</h3>
                                    </div>

                                    <div className="grid grid-cols-2 gap-4 mb-4">
                                        <div>
                                            <div className="text-sm text-slate-400">Current Level</div>
                                            <div className="text-2xl font-bold">{cityData[selectedCity].noise.current.toFixed(0)}</div>
                                            <div className="text-xs text-slate-500">decibels</div>
                                        </div>
                                        <div>
                                            <div className="text-sm text-slate-400">Status</div>
                                            <div className={`text-lg font-bold ${cityData[selectedCity].noise.current > 95 ? 'text-red-400' :
                                                    cityData[selectedCity].noise.current > 85 ? 'text-yellow-400' :
                                                        'text-green-400'
                                                }`}>
                                                {cityData[selectedCity].noise.current > 95 ? 'Harmful' :
                                                    cityData[selectedCity].noise.current > 85 ? 'Loud' : 'Normal'}
                                            </div>
                                        </div>
                                    </div>

                                    <div className="bg-slate-800 rounded p-3 text-sm">
                                        <div className="text-slate-400 mb-2">Health Impact:</div>
                                        <div className="text-xs space-y-1">
                                            {cityData[selectedCity].noise.current > 85 && (
                                                <div className="text-yellow-400">⚠️ Prolonged exposure may cause hearing damage</div>
                                            )}
                                            {cityData[selectedCity].noise.current > 95 && (
                                                <div className="text-red-400">🚨 Immediate hearing protection required</div>
                                            )}
                                            {cityData[selectedCity].noise.current <= 85 && (
                                                <div className="text-green-400">✓ Levels within safe limits</div>
                                            )}
                                        </div>
                                    </div>
                                </div>

                                {/* Air Quality */}
                                <div className="bg-slate-700/50 rounded-lg p-4">
                                    <div className="flex items-center space-x-2 mb-3">
                                        <Wind className="w-5 h-5 text-cyan-400" />
                                        <h3 className="font-semibold">Air Quality Index</h3>
                                    </div>

                                    <div className="mb-4">
                                        <div className="flex items-end justify-between mb-2">
                                            <div>
                                                <div className="text-3xl font-bold">{cityData[selectedCity].airQuality.current.toFixed(0)}</div>
                                                <div className="text-sm text-slate-400">{cityData[selectedCity].airQuality.level}</div>
                                            </div>
                                            <div className="text-right">
                                                <div className="text-xs text-slate-400">Recommendation</div>
                                                <div className="text-sm">
                                                    {cityData[selectedCity].airQuality.current > 150 ? '😷 Wear mask outdoors' :
                                                        cityData[selectedCity].airQuality.current > 100 ? '⚠️ Limit outdoor activities' :
                                                            '✓ Safe for outdoor activities'}
                                                </div>
                                            </div>
                                        </div>

                                        <div className="h-3 bg-slate-800 rounded-full overflow-hidden">
                                            <div
                                                className="h-full transition-all duration-500"
                                                style={{
                                                    width: `${Math.min(100, (cityData[selectedCity].airQuality.current / 200) * 100)}%`,
                                                    backgroundColor: cityData[selectedCity].airQuality.current > 150 ? '#ef4444' :
                                                        cityData[selectedCity].airQuality.current > 100 ? '#f59e0b' :
                                                            cityData[selectedCity].airQuality.current > 50 ? '#fbbf24' : '#10b981'
                                                }}
                                            ></div>
                                        </div>
                                    </div>

                                    <ResponsiveContainer width="100%" height={80}>
                                        <LineChart data={cityData[selectedCity].airQuality.trend.map((v, i) => ({ value: v, index: i }))}>
                                            <Line type="monotone" dataKey="value" stroke="#06b6d4" strokeWidth={2} dot={false} />
                                            <CartesianGrid stroke="#334155" strokeDasharray="3 3" />
                                        </LineChart>
                                    </ResponsiveContainer>
                                </div>

                                {/* Temperature & Humidity */}
                                <div className="bg-slate-700/50 rounded-lg p-4">
                                    <div className="flex items-center space-x-2 mb-3">
                                        <Thermometer className="w-5 h-5 text-red-400" />
                                        <h3 className="font-semibold">Climate Conditions</h3>
                                    </div>

                                    <div className="grid grid-cols-2 gap-4">
                                        <div className="bg-slate-800 rounded p-3 text-center">
                                            <div className="text-sm text-slate-400 mb-1">Temperature</div>
                                            <div className="text-3xl font-bold">{cityData[selectedCity].temperature.current.toFixed(1)}°C</div>
                                            <div className="text-xs text-slate-500 mt-1">
                                                Feels {cityData[selectedCity].temperature.current > 30 ? 'Hot' :
                                                    cityData[selectedCity].temperature.current > 20 ? 'Warm' :
                                                        cityData[selectedCity].temperature.current > 10 ? 'Cool' : 'Cold'}
                                            </div>
                                        </div>
                                        <div className="bg-slate-800 rounded p-3 text-center">
                                            <div className="text-sm text-slate-400 mb-1">Humidity</div>
                                            <div className="text-3xl font-bold">{cityData[selectedCity].temperature.humidity.toFixed(0)}%</div>
                                            <div className="text-xs text-slate-500 mt-1">
                                                {cityData[selectedCity].temperature.humidity > 70 ? 'Humid' :
                                                    cityData[selectedCity].temperature.humidity > 40 ? 'Comfortable' : 'Dry'}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* Alerts Panel */}
                <div className="w-80 bg-slate-800/95 backdrop-blur-lg border-l border-slate-700 overflow-y-auto">
                    <div className="sticky top-0 bg-slate-800 border-b border-slate-700 p-4 z-10">
                        <div className="flex items-center space-x-2 mb-2">
                            <AlertTriangle className="w-5 h-5 text-yellow-400" />
                            <h2 className="text-lg font-bold">Live Alerts</h2>
                        </div>
                        <div className="flex items-center justify-between text-sm text-slate-400">
                            <span>{alerts.length} total</span>
                            <button
                                onClick={() => setAlerts([])}
                                className="text-xs hover:text-white transition-colors"
                            >
                                Clear All
                            </button>
                        </div>
                    </div>

                    <div className="p-4 space-y-3">
                        {alerts.length === 0 ? (
                            <div className="text-center py-12 text-slate-500">
                                <Activity className="w-12 h-12 mx-auto mb-3 opacity-30" />
                                <p>No alerts at the moment</p>
                                <p className="text-xs mt-1">All systems operating normally</p>
                            </div>
                        ) : (
                            alerts.map(alert => (
                                <div
                                    key={alert.id}
                                    className={`p-3 rounded-lg border-l-4 cursor-pointer transition-all hover:scale-[1.02] ${alert.severity === 'CRITICAL'
                                            ? 'bg-red-500/10 border-red-500'
                                            : 'bg-yellow-500/10 border-yellow-500'
                                        }`}
                                    onClick={() => setSelectedCity(alert.city)}
                                >
                                    <div className="flex items-start justify-between mb-2">
                                        <span className={`px-2 py-1 rounded text-xs font-bold ${alert.severity === 'CRITICAL'
                                                ? 'bg-red-500 text-white'
                                                : 'bg-yellow-500 text-black'
                                            }`}>
                                            {alert.severity}
                                        </span>
                                        <span className="text-xs text-slate-400">
                                            {alert.timestamp.toLocaleTimeString()}
                                        </span>
                                    </div>
                                    <div className="font-semibold text-sm mb-1">{alert.type}</div>
                                    <div className="text-xs text-slate-300 mb-2">{alert.message}</div>
                                    <div className="flex items-center space-x-2 text-xs">
                                        <MapPin className="w-3 h-3" />
                                        <span className="text-slate-400">{alert.city}</span>
                                    </div>
                                    {!alert.acknowledged && (
                                        <button
                                            onClick={(e) => acknowledgeAlert(alert.id, e)}
                                            className="mt-2 w-full py-1 bg-slate-700 hover:bg-slate-600 rounded text-xs transition-colors"
                                        >
                                            Acknowledge
                                        </button>
                                    )}
                                </div>
                            ))
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}