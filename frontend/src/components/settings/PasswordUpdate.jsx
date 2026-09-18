import { FaEye } from "react-icons/fa";

function PasswordUpdate() {
  return (
    <div className="rounded-3xl bg-white p-6 shadow-sm border border-gray-100">

      <h2 className="mb-6 text-2xl font-bold">
        Password Update
      </h2>

      <div className="space-y-5">

        <div>

          <label className="mb-2 block font-medium">
            Current Password
          </label>

          <div className="relative">

            <input
              type="password"
              defaultValue="password123"
              className="w-full rounded-xl border border-gray-300 px-4 py-3 pr-12"
            />

            <FaEye className="absolute right-4 top-4 text-gray-400" />

          </div>

        </div>

        <div>

          <label className="mb-2 block font-medium">
            New Password
          </label>

          <div className="relative">

            <input
              type="password"
              defaultValue="newpassword"
              className="w-full rounded-xl border border-gray-300 px-4 py-3 pr-12"
            />

            <FaEye className="absolute right-4 top-4 text-gray-400" />

          </div>

        </div>

      </div>

      <button className="mt-6 rounded-xl bg-green-700 px-6 py-3 font-semibold text-white hover:bg-green-800">

        Update Password

      </button>

    </div>
  );
}

export default PasswordUpdate;