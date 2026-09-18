const devices = [
  {
    id: "SN-01",
    desc: "Soil & temperature node — Zone A",
    status: "Online",
    color: "bg-green-100 text-green-700",
  },
  {
    id: "SN-05",
    desc: "pH & moisture node — Zone B",
    status: "Online",
    color: "bg-green-100 text-green-700",
  },
  {
    id: "SN-09",
    desc: "Weather node — Zone C",
    status: "Offline",
    color: "bg-red-100 text-red-600",
  },
  {
    id: "DR-02",
    desc: "Scouting drone",
    status: "Idle",
    color: "bg-gray-100 text-gray-500",
  },
];

function ConnectedDevices() {
  return (
    <div className="rounded-3xl bg-white p-6 shadow-sm border border-gray-100">

      <h2 className="mb-6 text-2xl font-bold">
        Connected IoT Devices
      </h2>

      <div className="space-y-4">

        {devices.map((device) => (
          <div
            key={device.id}
            className="flex items-center justify-between rounded-2xl border border-gray-200 p-4"
          >
            <div>

              <h4 className="font-semibold">
                {device.id}
              </h4>

              <p className="text-sm text-gray-500">
                {device.desc}
              </p>

            </div>

            <span
              className={`rounded-full px-4 py-1 text-sm font-semibold ${device.color}`}
            >
              {device.status}
            </span>

          </div>
        ))}

      </div>

    </div>
  );
}

export default ConnectedDevices;