function StatCard({
  icon,
  title,
  value,
  status,
  statusColor = "text-green-600",
  iconBg = "bg-green-100",
}) {
  return (
    <div className="bg-white rounded-2xl p-6 shadow-sm border border-gray-100 hover:shadow-lg transition-all duration-300">

      {/* Header */}

      <div className="flex items-center justify-between">

        <div
          className={`w-10 h-10 rounded-lg flex items-center justify-center ${iconBg}`}
        >
          {icon}
        </div>

        <span className={`text-xs font-semibold ${statusColor}`}>
          {status}
        </span>

      </div>

      {/* Content */}

      <div className="mt-5">

        <p className="text-gray-500 text-sm">
          {title}
        </p>

        <h2 className="text-3xl font-bold mt-2 text-gray-800">
          {value}
        </h2>

      </div>

    </div>
  );
}

export default StatCard;