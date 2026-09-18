import { useRef, useState } from "react";

import Sidebar from "../components/dashboard/Sidebar";
import SensorHeader from "../components/sensors/SensorHeader";
import SensorGrid from "../components/sensors/SensorGrid";
import AIRecommendation from "../components/sensors/AIRecommendation";
import NetworkHealth from "../components/sensors/NetworkHealth";
import CameraStream from "../components/sensors/CameraStream";

function Sensors() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Sensor connection state
  const [sensorConnected, setSensorConnected] = useState(false);

  // Used when the user manually clicks Connect Sensor
  const [connectRequest, setConnectRequest] = useState(0);

  const cameraRef = useRef(null);

  return (
    <div className="flex min-h-screen bg-green-50">

      <Sidebar
        sidebarOpen={sidebarOpen}
        setSidebarOpen={setSidebarOpen}
        sensorConnected={sensorConnected}
        setSensorConnected={setSensorConnected}
        setConnectRequest={setConnectRequest}
        cameraRef={cameraRef}
      />

      <main className="flex-1 p-6 lg:ml-72">

        <SensorHeader
          setSidebarOpen={setSidebarOpen}
        />

        <SensorGrid />

        <div className="mt-8 grid gap-6 lg:grid-cols-3">

          {/* Left Side */}
          <div className="lg:col-span-2">

            <CameraStream
              ref={cameraRef}
              setSensorConnected={setSensorConnected}
              connectRequest={connectRequest}
            />

          </div>

          {/* Right Side */}
          <div>

            <AIRecommendation />

            <NetworkHealth />

          </div>

        </div>

      </main>

    </div>
  );
}

export default Sensors;