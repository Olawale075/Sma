import Sidebar from "../components/dashboard/Sidebar";
import { useState } from "react";

import UploadCard from "../components/detection/UploadCard";
import PredictionCard from "../components/detection/PredictionCard";
import { FaBars } from "react-icons/fa";

function Detection() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  return (
    <div className="flex min-h-screen bg-[#F7FBF8]">

      <Sidebar />

      <div className="flex flex-1 flex-col lg:ml-72 p-6 md:p-8">

        <Sidebar
          sidebarOpen={sidebarOpen}
          setSidebarOpen={setSidebarOpen}
        />

        <main>

          <div className="flex items-center gap-4">
            <button
              className="lg:hidden text-2xl"
              onClick={() => setSidebarOpen(true)}
            >
              <FaBars />
            </button>
            <div>
              <h1 className="text-4xl font-bold text-gray-800">
                Disease Detection
              </h1>

              <p className="mt-2 text-gray-500">
                Upload a leaf or canopy image and the model returns a diagnosis in seconds.
              </p>
            </div>
          </div>

          <div className="mt-8 grid gap-6 lg:grid-cols-2">

            <UploadCard />

            <PredictionCard />

          </div>

        </main>

      </div>

    </div>
  );
}

export default Detection;