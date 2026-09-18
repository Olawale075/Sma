import { FaRobot, FaArrowRight } from "react-icons/fa";

function AIRecommendation() {
  return (
    <div className="rounded-2xl bg-gradient-to-br from-green-700 to-green-600 p-6 text-white shadow-lg">

      <div className="flex items-center gap-3">

        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-white/20">
          <FaRobot size={22} />
        </div>

        <div>
          <h2 className="text-xl font-bold">
            AI Recommendation
          </h2>

          <p className="text-green-100 text-sm">
            Generated from live sensor data
          </p>
        </div>

      </div>

      <div className="mt-6 space-y-5">

        <div>
          <p className="text-green-100 text-sm">
            Detected Issue
          </p>

          <h3 className="text-lg font-semibold">
            Low Soil Moisture
          </h3>
        </div>

        <div>
          <p className="text-green-100 text-sm">
            Confidence
          </p>

          <h3 className="text-lg font-semibold">
            96%
          </h3>
        </div>

        <div>
          <p className="text-green-100 text-sm">
            Recommendation
          </p>

          <p className="mt-1">
            Increase irrigation by approximately
            <span className="font-bold"> 15 Litres </span>
            over the next four hours.
          </p>
        </div>

      </div>

      <button className="mt-8 flex w-full items-center justify-center gap-2 rounded-xl bg-white py-3 font-semibold text-green-700 transition hover:scale-105">

        Execute Plan

        <FaArrowRight />

      </button>

    </div>
  );
}

export default AIRecommendation;