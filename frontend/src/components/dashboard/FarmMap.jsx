import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet";
import { FaSatelliteDish } from "react-icons/fa";

function FarmMap() {
  return (
    <div className="bg-white rounded-2xl shadow-sm p-6">

      {/* Header */}

      <div className="flex justify-between items-center mb-5">

        <h2 className="text-xl font-bold">
          Interactive Farm Map
        </h2>

        <div className="flex gap-3">

          <button className="px-4 py-2 rounded-lg bg-gray-100 hover:bg-gray-200">
            Satellite
          </button>

          <button className="px-4 py-2 rounded-lg bg-green-700 text-white">
            Sensors
          </button>

        </div>

      </div>

      {/* Map */}

      <MapContainer
        center={[6.5244, 3.3792]}
        zoom={13}
        scrollWheelZoom={true}
        className="h-[500px] rounded-xl"
      >
        <TileLayer
          attribution="OpenStreetMap"
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        {/* Example Sensor */}

        <Marker position={[6.5244, 3.3792]}>
          <Popup>

            <div>

              <strong>Sensor A</strong>

              <br />

              Soil Moisture: 64%

              <br />

              Temperature: 28°C

            </div>

          </Popup>
        </Marker>

      </MapContainer>

    </div>
  );
}

export default FarmMap;