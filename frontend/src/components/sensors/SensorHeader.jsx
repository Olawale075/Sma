import { FaBars, FaUsers, FaClock } from "react-icons/fa";

function SensorHeader({ setSidebarOpen }) {
  return (
    <div className="mb-8">

      {/* Top Row */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">

        {/* Left */}
        <div className="flex items-center gap-4">
          <button
          className="lg:hidden text-2xl"
          onClick={() => setSidebarOpen(true)}
        >
          <FaBars />
        </button>
          <div>

          <div className="inline-flex items-center gap-2 rounded-full bg-green-100 px-4 py-2">
            <span className="h-2 w-2 rounded-full bg-green-500 animate-pulse"></span>

            <span className="text-xs font-semibold uppercase tracking-wider text-green-700">
              Real-Time Network
            </span>
          </div>

          <h1 className="mt-4 text-4xl font-bold text-gray-800">
            Sensor Monitoring
          </h1>

          <p className="mt-2 text-gray-500">
            Live stream from <span className="font-semibold">14 Active IoT Nodes</span> across Sector 7G.
          </p>

        </div>
        </div>

        {/* Right */}

        <div className="flex flex-wrap gap-4">

          <div className="flex items-center gap-3 rounded-full bg-white px-5 py-3 shadow-sm">

            <div className="flex -space-x-2">

              <img
                src="https://i.pravatar.cc/40?img=1"
                alt=""
                className="h-8 w-8 rounded-full border-2 border-white"
              />

              <img
                src="https://i.pravatar.cc/40?img=2"
                alt=""
                className="h-8 w-8 rounded-full border-2 border-white"
              />

            </div>

            <div className="flex items-center gap-2">

              <FaUsers className="text-green-700" />

              <span className="text-sm font-medium">
                2 Users Online
              </span>

            </div>

          </div>

          <div className="flex items-center gap-2 rounded-full bg-green-50 px-5 py-3">

            <FaClock className="text-green-700" />

            <span className="text-sm font-medium text-green-700">
              Last update: 2s ago
            </span>

          </div>

        </div>

      </div>

    </div>
  );
}

export default SensorHeader;