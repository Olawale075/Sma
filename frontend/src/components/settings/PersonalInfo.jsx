import { useState } from "react";

const API_URL = import.meta.env.VITE_API_URL || "https://fireeyes-detector-ib01.onrender.com";
const LANGUAGE_STORAGE_KEY = "smartEyes.aiLanguage";

function getStoredUser() {
  const storedUser = localStorage.getItem("user");
  if (!storedUser) return null;

  try {
    return JSON.parse(storedUser);
  } catch (error) {
    console.error("Failed to parse stored user:", error);
    return null;
  }
}

function PersonalInfo() {
  const [user, setUser] = useState(getStoredUser);
  const [saveError, setSaveError] = useState("");
  const [formData, setFormData] = useState(() => {
    const storedUser = getStoredUser() || {};
    return {
      name: storedUser.name || storedUser.fullName || storedUser.firstName || "",
      email: storedUser.email || "",
      phoneNumbers: storedUser.phoneNumbers || "",
      farmName: storedUser.farmName || "",
      preferredLanguage: storedUser.preferredLanguage || "yo",
    };
  });

  // HANDLE INPUT CHANGES
  const handleChange = (e) => {
    const { name, value } = e.target;

    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  // SAVE CHANGES
  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaveError("");

    const token = localStorage.getItem("token");
    if (!token) {
      setSaveError("Please sign in again before saving your preferences.");
      return;
    }

    const phoneNumber = formData.phoneNumbers || user?.phoneNumber;
    if (!phoneNumber) {
      setSaveError("Your account phone number is missing.");
      return;
    }

    try {
      const response = await fetch(
        `${API_URL}/user/user/${encodeURIComponent(phoneNumber)}`,
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({
            ...user,
            name: formData.name,
            email: formData.email,
            phoneNumbers: phoneNumber,
            farmName: formData.farmName,
            crop: user?.crop,
            farmLocation: user?.farmLocation,
            farmSize: user?.farmSize,
            preferredLanguage: formData.preferredLanguage,
          }),
        }
      );

      const responseBody = await response.text();
      let updatedProfile;
      try {
        updatedProfile = responseBody ? JSON.parse(responseBody) : {};
      } catch {
        throw new Error(responseBody || "Could not save your profile.");
      }

      if (!response.ok) {
        throw new Error(
          updatedProfile.message || updatedProfile.error || "Could not save your profile."
        );
      }

      const updatedUser = {
        ...user,
        ...updatedProfile,
        phoneNumbers: updatedProfile.phoneNumbers || phoneNumber,
        preferredLanguage: updatedProfile.preferredLanguage || formData.preferredLanguage,
      };
      localStorage.setItem("user", JSON.stringify(updatedUser));
      localStorage.setItem(LANGUAGE_STORAGE_KEY, updatedUser.preferredLanguage);
      setUser(updatedUser);
      window.dispatchEvent(new Event("userUpdated"));
    } catch (error) {
      setSaveError(error.message || "Could not save your profile.");
    }

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

          <div>
            <label className="mb-2 block font-medium text-gray-700">
              Preferred Language
            </label>

            <select
              name="preferredLanguage"
              value={formData.preferredLanguage}
              onChange={handleChange}
              className="w-full rounded-xl border border-gray-300 px-4 py-3 focus:outline-none focus:ring-2 focus:ring-green-500"
            >
              <option value="yo">Yoruba</option>
              <option value="en">English</option>
              <option value="fr">French</option>
            </select>
          </div>

        </div>

        {saveError && <p className="mt-4 text-sm text-red-600">{saveError}</p>}

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