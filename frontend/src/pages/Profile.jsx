import Sidebar from "../components/dashboard/Sidebar";
import { useState } from "react";

import ProfileCard from "../components/profile/ProfileCard";
import FarmDetails from "../components/profile/FarmDetails";
import RecentActivity from "../components/profile/RecentActivity";
import { FaBars } from "react-icons/fa";

function Profile() {
    const [sidebarOpen, setSidebarOpen] = useState(false);
  return (
    <div className="flex min-h-screen bg-[#F5FAF5]">

      <Sidebar />

      <div className="flex flex-1 flex-col lg:ml-72 p-6 md:p-8">
        <Sidebar
          sidebarOpen={sidebarOpen}
          setSidebarOpen={setSidebarOpen}
        />

        <main className="flex-1">

          {/* Header */}

          <div>
            <button
                className="lg:hidden text-2xl"
                onClick={() => setSidebarOpen(true)}
            >
                <FaBars />
            </button>
            <div className="mb-10 flex flex-col justify-between gap-6 md:flex-row md:items-center">

            <div>

              <h1 className="text-4xl font-bold text-gray-800">
                Farmer Profile
              </h1>

              <p className="mt-3 text-gray-500">
                Account and farm information used to personalise predictions.
              </p>

            </div>

            <button className="rounded-xl border border-gray-200 bg-white px-6 py-3 shadow-sm hover:bg-gray-50">
              Edit Profile
            </button>

          </div>
          </div>

          {/* Content */}

          <div className="grid gap-8 xl:grid-cols-2">

            <ProfileCard />

            <div className="space-y-8">

              <FarmDetails />

              <RecentActivity />

            </div>

          </div>

        </main>

      </div>

    </div>
  );
}

export default Profile;