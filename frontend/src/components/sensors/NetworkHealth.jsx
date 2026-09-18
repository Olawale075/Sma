import {
  FaWifi,
  FaBatteryThreeQuarters,
  FaServer,
} from "react-icons/fa";

function ProgressBar({ value, color }) {
  return (
    <div className="mt-2 h-2 w-full rounded-full bg-gray-200">

      <div
        className={`h-2 rounded-full ${color}`}
        style={{ width: `${value}%` }}
      ></div>

    </div>
  );
}

function NetworkHealth() {
  return (
    <div className="mt-6 rounded-2xl bg-white p-6 shadow-sm">

      <h2 className="text-xl font-bold text-gray-800">
        Network Health
      </h2>

      <div className="mt-6 space-y-6">

        <div>

          <div className="flex justify-between">

            <div className="flex items-center gap-2">

              <FaServer className="text-green-700" />

              Active Nodes

            </div>

            <span className="font-semibold">
              14 / 15
            </span>

          </div>

          <ProgressBar value={93} color="bg-green-600" />

        </div>

        <div>

          <div className="flex justify-between">

            <div className="flex items-center gap-2">

              <FaWifi className="text-blue-600" />

              Signal Strength

            </div>

            <span className="font-semibold">
              Excellent
            </span>

          </div>

          <ProgressBar value={95} color="bg-blue-600" />

        </div>

        <div>

          <div className="flex justify-between">

            <div className="flex items-center gap-2">

              <FaBatteryThreeQuarters className="text-yellow-500" />

              Battery Health

            </div>

            <span className="font-semibold">
              91%
            </span>

          </div>

          <ProgressBar value={91} color="bg-yellow-500" />

        </div>

      </div>

    </div>
  );
}

export default NetworkHealth;