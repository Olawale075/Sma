import {
  FaExclamationTriangle,
  FaTint,
  FaCheckCircle,
} from "react-icons/fa";

const alerts = [
  {
    title: "High Leaf Rust Risk",
    subtitle: "Sector 4 • 2 hours ago",
    color: "text-red-600",
    icon: <FaExclamationTriangle />,
  },
  {
    title: "Soil Moisture Low",
    subtitle: "Corn Field • 5 hours ago",
    color: "text-yellow-600",
    icon: <FaTint />,
  },
  {
    title: "Irrigation Completed",
    subtitle: "Sector 1 • Yesterday",
    color: "text-green-600",
    icon: <FaCheckCircle />,
  },
];

function RecentAlerts() {
  return (
    <div className="bg-white rounded-2xl shadow-sm p-6">

      <h2 className="text-xl font-bold mb-6">
        Recent Alerts
      </h2>

      <div className="space-y-5">

        {alerts.map((alert) => (

          <div
            key={alert.title}
            className="flex gap-4 p-4 rounded-xl bg-gray-50"
          >

            <div className={alert.color}>
              {alert.icon}
            </div>

            <div>

              <h4 className="font-semibold">
                {alert.title}
              </h4>

              <p className="text-sm text-gray-500">
                {alert.subtitle}
              </p>

            </div>

          </div>

        ))}

      </div>

    </div>
  );
}

export default RecentAlerts;