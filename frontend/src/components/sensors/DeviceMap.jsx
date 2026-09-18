import {
  MapContainer,
  TileLayer,
  Marker,
  Popup,
} from "react-leaflet";

import L from "leaflet";
import "leaflet/dist/leaflet.css";

const greenIcon = new L.Icon({
  iconUrl:
    "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-green.png",
  shadowUrl:
    "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});

const yellowIcon = new L.Icon({
  iconUrl:
    "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-yellow.png",
  shadowUrl:
    "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});

const redIcon = new L.Icon({
  iconUrl:
    "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-red.png",
  shadowUrl:
    "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});

const sensors = [
  {
    id: "Sensor A",
    position: [6.5244, 3.3792],
    status: "Healthy",
    battery: "91%",
    temperature: "24°C",
    humidity: "63%",
    icon: greenIcon,
  },
  {
    id: "Sensor B",
    position: [6.5265, 3.3825],
    status: "Warning",
    battery: "58%",
    temperature: "31°C",
    humidity: "45%",
    icon: yellowIcon,
  },
  {
    id: "Sensor C",
    position: [6.5225, 3.3774],
    status: "Offline",
    battery: "8%",
    temperature: "--",
    humidity: "--",
    icon: redIcon,
  },
];

function DeviceMap() {
  return (
    <div className="rounded-2xl bg-white p-6 shadow-sm">

      <div className="mb-6 flex items-center justify-between">

        <div>

          <h2 className="text-2xl font-bold">
            Device Distribution
          </h2>

          <p className="text-gray-500">
            Live locations of IoT sensors
          </p>

        </div>

        <button className="rounded-lg bg-green-700 px-4 py-2 text-white hover:bg-green-800">
          Fullscreen
        </button>

      </div>
      <div>
        <canvas id="streamCanvas" className="w-full h-[300px] sm:h-[400px] md:h-[500px] lg:h-[600px] rounded-xl bg-black"></canvas>
        <div class="video-overlay" id="videoOverlay">● OFFLINE</div>

        <div class="server-config">
            <span><span class="label">🔗 Server:</span> <span class="value" id="serverUrl">ws://localhost:8080/camera-stream</span></span>
            <span><span class="label">📡 Status:</span> <span id="serverStatus"><span class="status-dot offline" id="statusDot"></span> <span id="statusText">Offline</span></span></span>
            <span><span class="label">📦 Frames:</span> <span id="frameCounter">0</span></span>
        </div>
      </div>

      {/* <MapContainer
        center={[6.5244, 3.3792]}
        zoom={15}
        className="h-[450px] rounded-xl"
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution="© OpenStreetMap contributors"
        />

        {sensors.map((sensor) => (
          <Marker
            key={sensor.id}
            position={sensor.position}
            icon={sensor.icon}
          >
            <Popup>

              <div className="space-y-2">

                <h3 className="font-bold">
                  {sensor.id}
                </h3>

                <p>Status: {sensor.status}</p>

                <p>Battery: {sensor.battery}</p>

                <p>Temperature: {sensor.temperature}</p>

                <p>Humidity: {sensor.humidity}</p>

              </div>

            </Popup>
          </Marker>
        ))}

      </MapContainer> */}

      <div className="mt-5 flex flex-wrap gap-6">

        <div className="flex items-center gap-2">

          <span className="h-3 w-3 rounded-full bg-green-500"></span>

          Healthy

        </div>

        <div className="flex items-center gap-2">

          <span className="h-3 w-3 rounded-full bg-yellow-500"></span>

          Warning

        </div>

        <div className="flex items-center gap-2">

          <span className="h-3 w-3 rounded-full bg-red-500"></span>

          Offline

        </div>

      </div>

    </div>
  );
}

export default DeviceMap;