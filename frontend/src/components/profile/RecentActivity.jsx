const activities = [
  {
    title: "Uploaded 6 leaf images for analysis",
    time: "Today, 09:14",
  },
  {
    title: "Applied fungicide recommendation in Zone B",
    time: "Yesterday, 16:40",
  },
  {
    title: "Calibrated soil pH sensor SN-05",
    time: "2 days ago",
  },
  {
    title: "Added new sensor node SN-14",
    time: "5 days ago",
  },
];

function RecentActivity() {
  return (
    <div className="rounded-3xl border border-gray-100 bg-white p-8 shadow-sm">

      <h2 className="mb-6 text-3xl font-bold text-gray-800">
        Recent Activity
      </h2>

      {activities.map((activity) => (
        <div
          key={activity.title}
          className="flex items-center justify-between border-b border-gray-100 py-5 last:border-0"
        >
          <p className="text-gray-700">
            {activity.title}
          </p>

          <span className="text-gray-400">
            {activity.time}
          </span>
        </div>
      ))}

    </div>
  );
}

export default RecentActivity;