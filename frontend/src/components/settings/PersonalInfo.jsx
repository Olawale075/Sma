import { useEffect, useState } from "react";

function PersonalInfo() {
  const [user, setUser] = useState(null);

  const [formData, setFormData] = useState({
    name: "",
    email: "",
    phoneNumbers: "",
    farmName: "",
  });

  // LOAD USER FROM LOCAL STORAGE
  useEffect(() => {
    const storedUser = localStorage.getItem("user");

    console.log("PersonalInfo stored user:", storedUser);

    if (!storedUser) {
      return;
    }

    try {
      const parsedUser = JSON.parse(storedUser);

      console.log("PersonalInfo parsed user:", parsedUser);

      setUser(parsedUser);

      setFormData({
        name:
          parsedUser.name ||
          parsedUser.fullName ||
          parsedUser.firstName ||
          "",
        email: parsedUser.email || "",
        phoneNumbers: parsedUser.phoneNumbers || "",
        farmName: parsedUser.farmName || "",
      });
    } catch (error) {
      console.error("Failed to parse stored user:", error);
    }
  }, []);

  // HANDLE INPUT CHANGES
  const handleChange = (e) => {
    const { name, value } = e.target;

    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  // SAVE CHANGES
  const handleSubmit = (e) => {
    e.preventDefault();

    const updatedUser = {
      ...user,
      name: formData.name,
      email: formData.email,
      phoneNumbers: formData.phoneNumbers,
      farmName: formData.farmName,
    };

    // Update local storage
    localStorage.setItem("user", JSON.stringify(updatedUser));

    // Update React state
    setUser(updatedUser);

    alert("Personal information updated successfully!");
  };

  return (
    <div className="rounded-3xl bg-white p-6 shadow-sm border border-gray-100">

      <h2 className="mb-6 text-2xl font-bold text-gray-800">
        Personal Information
      </h2>

      <form onSubmit={handleSubmit}>
        <div className="grid gap-5 md:grid-cols-2">

          {/* Full Name */}
          <div>
            <label className="mb-2 block font-medium text-gray-700">
              Full Name
            </label>

            <input
              type="text"
              name="name"
              value={formData.name}
              onChange={handleChange}
              placeholder="Enter your full name"
              className="w-full rounded-xl border border-gray-300 px-4 py-3 focus:outline-none focus:ring-2 focus:ring-green-500"
            />
          </div>

          {/* Email */}
          <div>
            <label className="mb-2 block font-medium text-gray-700">
              Email Address
            </label>

            <input
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              placeholder="Enter your email address"
              className="w-full rounded-xl border border-gray-300 px-4 py-3 focus:outline-none focus:ring-2 focus:ring-green-500"
            />
          </div>

          {/* Phone Number */}
          <div>
            <label className="mb-2 block font-medium text-gray-700">
              Phone Number
            </label>

            <input
              type="text"
              name="phoneNumbers"
              value={formData.phoneNumbers}
              onChange={handleChange}
              placeholder="Enter your phone number"
              className="w-full rounded-xl border border-gray-300 px-4 py-3 focus:outline-none focus:ring-2 focus:ring-green-500"
            />
          </div>

          {/* Farm Name */}
          <div>
            <label className="mb-2 block font-medium text-gray-700">
              Farm Name
            </label>

            <input
              type="text"
              name="farmName"
              value={formData.farmName}
              onChange={handleChange}
              placeholder="Enter your farm name"
              className="w-full rounded-xl border border-gray-300 px-4 py-3 focus:outline-none focus:ring-2 focus:ring-green-500"
            />
          </div>

        </div>

        <button
          type="submit"
          className="mt-6 rounded-xl bg-green-700 px-6 py-3 font-semibold text-white transition hover:bg-green-800"
        >
          Save Changes
        </button>
      </form>

    </div>
  );
}

export default PersonalInfo;