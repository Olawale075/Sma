import { useRef, useState } from "react";

import Sidebar from "../components/dashboard/Sidebar";
import Topbar from "../components/dashboard/Topbar";
import StatsCards from "../components/dashboard/StatsCards";
import FarmMap from "../components/dashboard/FarmMap";
import RecentAlerts from "../components/dashboard/RecentAlerts";
import AIInsight from "../components/dashboard/AIInsight";
import CameraStream from "../components/sensors/CameraStream";

function Dashboard() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Sensor connection state
  const [sensorConnected, setSensorConnected] = useState(false);
  
  // Used when the user manually clicks Connect Sensor
  const [connectRequest, setConnectRequest] = useState(0);
  
  const cameraRef = useRef(null);

  return (
    <div className="flex bg-gray-50 min-h-screen">

      <Sidebar
        sidebarOpen={sidebarOpen}
        setSidebarOpen={setSidebarOpen}
        sensorConnected={sensorConnected}
        setSensorConnected={setSensorConnected}
        setConnectRequest={setConnectRequest}
        cameraRef={cameraRef}
      />

      <main className="flex-1 lg:ml-72 p-6 md:p-8">

        <Topbar setSidebarOpen={setSidebarOpen} />

        <StatsCards />

        <div className="grid lg:grid-cols-3 gap-8 mt-8">

          <div className="lg:col-span-2">
            {/* <FarmMap /> */}
            <CameraStream
              ref={cameraRef}
              setSensorConnected={setSensorConnected}
              connectRequest={connectRequest}
            />
          </div>

          <div>
            <RecentAlerts />
            <AIInsight />
          </div>

        </div>

        {/* <div className="grid lg:grid-cols-2 gap-8 mt-8">

          <DiseaseChart />

          <SensorTable />

        </div> */}

      </main>

    </div>
  );
}

export default Dashboard;