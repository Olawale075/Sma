import { FaBars, FaBell, FaCalendarAlt } from "react-icons/fa";

function Topbar({ setSidebarOpen }) {
  // Format today's date
  const today = new Date().toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  return (
    <header className="flex flex-col md:flex-row md:items-center md:justify-between gap-6 mb-10">

      {/* Left */}

      <div className="flex items-center gap-4">

        <button
          className="lg:hidden text-2xl"
          onClick={() => setSidebarOpen(true)}
        >
          <FaBars />
        </button>
        <div>
            <h1 className="text-4xl font-bold text-gray-800">
              Farm Overview
            </h1>

            <p className="text-gray-500 mt-2">
              Precision insights for Sector 7-G
              <span className="text-green-600 font-medium">
                {" "}• Real-time sync active
              </span>
            </p>
        </div>
      </div>

      {/* Right */}

      <div className="flex items-center gap-5">

        {/* Date */}

        <div className="flex items-center gap-3 bg-white shadow rounded-xl px-5 py-3">

          <FaCalendarAlt className="text-green-700" />

          <span className="text-gray-600 text-sm">
            {today}
          </span>

        </div>

        {/* Notification */}

        <button className="relative bg-white shadow rounded-xl p-3 hover:bg-gray-100 transition">

          <FaBell className="text-green-700 text-lg" />

          <span className="absolute top-2 right-2 w-2 h-2 rounded-full bg-red-500"></span>

        </button>

      </div>

    </header>
  );
}

export default Topbar;