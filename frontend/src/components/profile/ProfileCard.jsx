import { useEffect, useState } from "react";

function ProfileCard() {
  const [user, setUser] = useState(null);

  useEffect(() => {
    const loadUser = () => {
      const storedUser = localStorage.getItem("user");

      console.log("Profile stored user:", storedUser);

      if (!storedUser) {
        setUser(null);
        return;
      }

      try {
        const parsedUser = JSON.parse(storedUser);

        console.log("Profile parsed user:", parsedUser);

        setUser(parsedUser);
      } catch (error) {
        console.error("Failed to parse stored user:", error);
        setUser(null);
      }
    };

    loadUser();

    // Listen for changes to the logged-in user
    window.addEventListener("userUpdated", loadUser);

    return () => {
      window.removeEventListener("userUpdated", loadUser);
    };
  }, []);

  // ============================================================
  // USER NAME
  // ============================================================

  const displayName =
    user?.name ||
    user?.fullName ||
    user?.firstName ||
    user?.username ||
    "User";

  // ============================================================
  // AVATAR LETTER
  // ============================================================

  const firstLetter =
    displayName.trim().charAt(0).toUpperCase() || "U";

  // ============================================================
  // FARM INFORMATION
  // ============================================================

  const farmName =
    user?.farmName ||
    "Farm name not available";

  const farmLocation =
    user?.farmLocation ||
    user?.location ||
    "Location not available";

  // ============================================================
  // CROP
  // ============================================================

  const crop = user?.crop;

  return (
    <div className="flex flex-col items-center rounded-3xl border border-gray-100 bg-white p-10 shadow-sm">

      {/* ======================================================
          AVATAR
      ====================================================== */}

      <div className="flex h-28 w-28 items-center justify-center rounded-full bg-green-100">
        <span className="text-4xl font-bold text-green-700">
          {firstLetter}
        </span>
      </div>

      {/* ======================================================
          NAME
      ====================================================== */}

      <h2 className="mt-8 text-3xl font-bold text-gray-800">
        {displayName}
      </h2>

      {/* ======================================================
          FARM NAME + LOCATION
      ====================================================== */}

      <p className="mt-2 text-center text-gray-500">
        {farmName} • {farmLocation}
      </p>

      {/* ======================================================
          CROP
      ====================================================== */}

      {crop && (
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <span className="rounded-full bg-green-600 px-4 py-1 text-white">
            {crop}
          </span>
        </div>
      )}

    </div>
  );
}

export default ProfileCard;