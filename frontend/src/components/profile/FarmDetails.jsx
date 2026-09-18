import { useEffect, useState } from "react";

function FarmDetails() {
  const [user, setUser] = useState(null);

  useEffect(() => {
    const storedUser = localStorage.getItem("user");

    if (storedUser) {
      try {
        const parsedUser = JSON.parse(storedUser);

        console.log("Stored user:", parsedUser);

        setUser(parsedUser);
      } catch (error) {
        console.error("Failed to parse stored user:", error);
      }
    }
  }, []);

  return (
    <div className="rounded-3xl border border-gray-100 bg-white p-8 shadow-sm">

      <h2 className="mb-8 text-3xl font-bold text-gray-800">
        Farm Details
      </h2>

      <div className="grid gap-8 md:grid-cols-2">

        <Info
          title="Farm Name"
          value={user?.farmName || "Not provided"}
        />

        <Info
          title="Primary Crop"
          value={user?.crop || "Not provided"}
        />

        <Info
          title="Farm Size"
          value={user?.farmSize || "Not provided"}
        />

        <Info
          title="Phone"
          value={user?.phoneNumbers || "Not provided"}
        />

        <Info
          title="Email"
          value={user?.email || "Not provided"}
        />

        {/* <Info
          title="Farmer"
          value={user?.fullName || "Not provided"}
        /> */}

      </div>

    </div>
  );
}

function Info({ title, value }) {
  return (
    <div>
      <p className="text-sm uppercase tracking-wide text-gray-400">
        {title}
      </p>

      <h4 className="mt-2 text-xl font-semibold text-gray-800">
        {value}
      </h4>
    </div>
  );
}

export default FarmDetails;