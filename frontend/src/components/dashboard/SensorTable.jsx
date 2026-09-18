const sensors = [
  {
    id: "A01",
    moisture: "64%",
    temperature: "28°C",
    humidity: "70%",
    status: "Healthy",
  },
  {
    id: "A02",
    moisture: "41%",
    temperature: "31°C",
    humidity: "59%",
    status: "Warning",
  },
  {
    id: "A03",
    moisture: "22%",
    temperature: "33°C",
    humidity: "40%",
    status: "Critical",
  },
];

function SensorTable() {
  return (
    <div className="bg-white rounded-2xl shadow-sm p-6">

      <h2 className="text-xl font-bold mb-6">
        Live Sensor Data
      </h2>

      <table className="w-full">

        <thead>

          <tr className="text-left border-b">

            <th>ID</th>

            <th>Moisture</th>

            <th>Temp</th>

            <th>Humidity</th>

            <th>Status</th>

          </tr>

        </thead>

        <tbody>

          {sensors.map((sensor) => (

            <tr key={sensor.id} className="border-b h-14">

              <td>{sensor.id}</td>

              <td>{sensor.moisture}</td>

              <td>{sensor.temperature}</td>

              <td>{sensor.humidity}</td>

              <td>

                <span
                  className={`px-3 py-1 rounded-full text-sm

                  ${
                    sensor.status === "Healthy"
                      ? "bg-green-100 text-green-700"
                      : sensor.status === "Warning"
                      ? "bg-yellow-100 text-yellow-700"
                      : "bg-red-100 text-red-700"
                  }`}
                >
                  {sensor.status}
                </span>

              </td>

            </tr>

          ))}

        </tbody>

      </table>

    </div>
  );
}

export default SensorTable;