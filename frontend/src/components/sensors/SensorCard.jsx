function SensorCard({
  icon,
  title,
  value,
  unit,
  footerLeft,
  footerRight,
  status = "LIVE",
  statusColor = "bg-green-100 text-green-700",
}) {
  return (
    <div className="rounded-2xl bg-white p-6 shadow-sm border border-gray-100 hover:shadow-lg transition-all duration-300">

      {/* Header */}

      <div className="flex items-center justify-between">

        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-green-50 text-green-700">
          {icon}
        </div>

        <span
          className={`flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold ${statusColor}`}
        >
          <span className="h-2 w-2 rounded-full bg-green-500 animate-pulse"></span>

          {status}
        </span>

      </div>

      {/* Content */}

      <div className="mt-6">

        <p className="text-xs uppercase tracking-wide text-gray-500">
          {title}
        </p>

        <h2 className="mt-2 text-5xl font-bold text-gray-800">
          {value}
          <span className="ml-1 text-xl font-medium">
            {unit}
          </span>
        </h2>

      </div>

      {/* Footer */}

      <div className="mt-8 flex items-center justify-between border-t pt-4 text-sm">

        <span className="text-gray-500">
          {footerLeft}
        </span>

        <span className="font-semibold text-green-700">
          {footerRight}
        </span>

      </div>

    </div>
  );
}

export default SensorCard;