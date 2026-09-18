function NotificationItem({
  title,
  description,
  enabled,
}) {
  return (
    <div className="flex items-center justify-between py-4">

      <div>

        <h4 className="font-semibold text-gray-800">
          {title}
        </h4>

        <p className="text-sm text-gray-500">
          {description}
        </p>

      </div>

      <button
        className={`h-7 w-14 rounded-full transition ${
          enabled
            ? "bg-green-700"
            : "bg-gray-300"
        }`}
      >
        <div
          className={`h-6 w-6 rounded-full bg-white transition ${
            enabled
              ? "translate-x-7"
              : "translate-x-1"
          }`}
        />
      </button>

    </div>
  );
}

function NotificationPreferences() {
  return (
    <div className="rounded-3xl bg-white p-6 shadow-sm border border-gray-100">

      <h2 className="mb-6 text-2xl font-bold">
        Notification Preferences
      </h2>

      <NotificationItem
        title="Disease Risk Alerts"
        description="Notify when predicted risk exceeds 60%"
        enabled
      />

      <NotificationItem
        title="Sensor Offline Alerts"
        description="Notify when a node stops reporting"
        enabled
      />

      <NotificationItem
        title="Weather Warnings"
        description="Rainfall, heatwave and wind advisories"
        enabled
      />

      <NotificationItem
        title="Weekly Summary Email"
        description="Farm health digest every Monday"
        enabled={false}
      />

    </div>
  );
}

export default NotificationPreferences;