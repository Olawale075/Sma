import Sidebar from "../components/dashboard/Sidebar";
import { useState } from "react";

import PersonalInfo from "../components/settings/PersonalInfo";
import NotificationPreferences from "../components/settings/NotificationPreferences";
import PasswordUpdate from "../components/settings/PasswordUpdate";
import ConnectedDevices from "../components/settings/ConnectedDevices";
import { FaBars } from "react-icons/fa";

function Settings() {
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

          <div className="flex items-center gap-4">
            <button
                className="lg:hidden text-2xl"
                onClick={() => setSidebarOpen(true)}
            >
                <FaBars />
            </button>
            <div className="mb-8">

              <h1 className="text-4xl font-bold text-gray-800">
                Settings
              </h1>

              <p className="mt-2 text-gray-500">
                Account, notification and device preferences.
              </p>

            </div>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">

            <PersonalInfo />

            <NotificationPreferences />

            <PasswordUpdate />

            <ConnectedDevices />

          </div>

        </main>

      </div>

    </div>
  );
}

export default Settings;