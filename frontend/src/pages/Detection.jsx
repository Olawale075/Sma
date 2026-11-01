import Sidebar from "../components/dashboard/Sidebar";
import { useState } from "react";

import UploadCard from "../components/detection/UploadCard";
import PredictionCard from "../components/detection/PredictionCard";
import { FaBars } from "react-icons/fa";

const API_URL = import.meta.env.VITE_API_URL || "https://fireeyes-detector-ib01.onrender.com";

function Detection() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [prediction, setPrediction] = useState(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [error, setError] = useState("");

  async function analyzeImage(image) {
    const token = localStorage.getItem("token");
    if (!token) {
      setError("Please sign in before analyzing an image.");
      return;
    }

    const formData = new FormData();
    formData.append("image", image);
    setIsAnalyzing(true);
    setError("");
    setPrediction(null);

    try {
      const response = await fetch(`${API_URL}/api/detect/image`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      });
      const contentType = response.headers.get("content-type");
      const data = contentType?.includes("application/json")
        ? await response.json()
        : await response.text();

      if (!response.ok) {
        throw new Error(typeof data === "object" ? data.error || data.message : data);
      }

      setPrediction(data);
      const storedUser = localStorage.getItem("user");
      if (storedUser && data.aiTokenBalance != null) {
        localStorage.setItem(
          "user",
          JSON.stringify({ ...JSON.parse(storedUser), AITokenBalance: data.aiTokenBalance })
        );
        window.dispatchEvent(new Event("userUpdated"));
      }
    } catch (analysisError) {
      setError(analysisError.message || "Image analysis failed.");
    } finally {
      setIsAnalyzing(false);
    }
  }

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

            <UploadCard onAnalyze={analyzeImage} isAnalyzing={isAnalyzing} error={error} />

            <PredictionCard prediction={prediction} />

          </div>

        </main>

      </div>

    </div>
  );
}

export default Detection;