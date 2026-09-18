import {
  FaHome,
  FaSeedling,
  FaBroadcastTower,
  FaCog,
  FaPlusCircle,
  FaTimes,
  FaUser,
  FaSignOutAlt,
} from "react-icons/fa";

import { NavLink } from "react-router-dom";
import { logout } from "../../services/auth";
import { useEffect, useState } from "react";

const menuItems = [
  {
    title: "Overview",
    icon: <FaHome />,
    path: "/dashboard",
  },
  {
    title: "Disease Detection",
    icon: <FaSeedling />,
    path: "/detect",
  },
  {
    title: "Sensor Monitoring",
    icon: <FaBroadcastTower />,
    path: "/sensors",
  },
  {
    title: "Farmer Profile",
    icon: <FaUser />,
    path: "/profile",
  },
  {
    title: "Settings",
    icon: <FaCog />,
    path: "/settings",
  },
];

function Sidebar({
  sidebarOpen,
  setSidebarOpen,
  sensorConnected,
  cameraRef,
}) {
  const [user, setUser] = useState(null);

  // LOAD USER FROM LOCAL STORAGE
  const loadUser = () => {
    const storedUser = localStorage.getItem("user");

    // console.log("Sidebar stored user:", storedUser);

    if (!storedUser) {
      setUser(null);
      return;
    }

    try {
      const parsedUser = JSON.parse(storedUser);

      // console.log("Sidebar parsed user:", parsedUser);

      setUser(parsedUser);
    } catch (error) {
      console.error("Failed to parse stored user:", error);
      setUser(null);
    }
  };

  // LOAD USER WHEN SIDEBAR MOUNTS
  useEffect(() => {
    loadUser();

    // Listen for login/logout changes
    const handleUserUpdated = () => {
      loadUser();
    };

    window.addEventListener(
      "userUpdated",
      handleUserUpdated
    );

    return () => {
      window.removeEventListener(
        "userUpdated",
        handleUserUpdated
      );
    };
  }, []);

  const displayName =
    user?.name ||
    user?.fullName ||
    user?.firstName ||
    user?.username ||
    "User";

  const firstLetter = displayName
    ?.trim()
    ?.charAt(0)
    ?.toUpperCase() || "U";


  const handleLogout = async () => {
    try {
      await logout();

      // Send user back to login page
      window.location.href = "/login";
    } catch (error) {
      console.error("Logout failed:", error);
    }
  };

  return (
    <>
      {/* MOBILE OVERLAY */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/40 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* SIDEBAR */}
      <aside
        className={`
          fixed top-0 left-0 z-50
          h-screen w-64 md:w-72
          bg-[#EEF7F0]
          border-r border-gray-200
          flex flex-col justify-between
          overflow-y-auto
          transition-all duration-300 ease-in-out

          ${sidebarOpen
            ? "translate-x-0"
            : "-translate-x-full"
          }

          lg:translate-x-0
        `}
      >
        {/* TOP */}
        <div>
          {/* HEADER */}
          <div className="px-6 py-6">
            {/* Mobile */}
            <div className="flex items-center justify-between lg:hidden">
              <div>
                <h1 className="text-3xl font-extrabold text-green-700">
                  AgriAI
                </h1>

                <p className="mt-1 text-xs uppercase tracking-widest text-gray-500">
                  Enterprise Dashboard
                </p>
              </div>

              <button
                onClick={() =>
                  setSidebarOpen(false)
                }
                className="text-2xl text-gray-600 transition hover:text-green-700"
              >
                <FaTimes />
              </button>
            </div>

            {/* Desktop */}
            <div className="hidden lg:block">
              <h1 className="text-4xl font-extrabold text-green-700">
                AgriAI
              </h1>

              <p className="mt-1 text-sm uppercase tracking-widest text-gray-500">
                Enterprise Dashboard
              </p>
            </div>
          </div>

          {/* NAVIGATION */}
          <nav className="mt-6">
            {menuItems.map((item) => (
              <NavLink
                key={item.title}
                to={item.path}
                onClick={() =>
                  setSidebarOpen(false)
                }
                className={({ isActive }) =>
                  `flex w-full items-center gap-4 px-8 py-4 transition ${
                    isActive
                      ? "bg-green-100 text-green-700 font-semibold"
                      : "text-gray-600 hover:bg-green-50"
                  }`
                }
              >
                {item.icon}
                {item.title}
              </NavLink>
            ))}
          </nav>
        </div>

        {/* FOOTER */}
        <div className="border-t border-gray-200 p-6">

          {/* SENSOR BUTTON */}
          <button
            onClick={() => {
              if (sensorConnected) {
                cameraRef.current?.disconnect();
              } else {
                cameraRef.current?.connect();
              }
            }}
            className={`
              flex w-full items-center justify-center
              gap-2 rounded-xl py-3
              font-medium text-white transition
              ${
                sensorConnected
                  ? "bg-red-600 hover:bg-red-700"
                  : "bg-green-700 hover:bg-green-800"
              }
            `}
          >
            {sensorConnected ? (
              <>
                <FaTimes />
                Disconnect Sensor
              </>
            ) : (
              <>
                <FaPlusCircle />
                Connect Sensor
              </>
            )}
          </button>

          {/* USER */}
          <div className="mt-8 flex items-center gap-4">

            {/* Avatar */}
            <div
              className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-green-700 text-lg font-bold text-white"
            >
              {user?.name
                ? user.name.trim().charAt(0).toUpperCase()
                : "U"}
            </div>

            {/* User information */}
            <div className="min-w-0 flex-1">
              <h4
                className="truncate font-semibold text-gray-800"
              >
                {user?.name || "User"}
              </h4>

              {/* <p className="text-sm text-gray-500">
                Farmer
              </p> */}
            </div>
          </div>

          {/* LOGOUT */}
          <button
            className=" mt-8 flex items-center gap-3 text-gray-600 transition hover:text-red-600"
            onClick={handleLogout}
          >
            <FaSignOutAlt />
            <span>Logout</span>
          </button>
        </div>
      </aside>
    </>
  );
}

export default Sidebar;
