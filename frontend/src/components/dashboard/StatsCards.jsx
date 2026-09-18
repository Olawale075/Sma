import {
  FaLeaf,
  FaBug,
  FaBroadcastTower,
  FaCloudSun,
} from "react-icons/fa";

import StatCard from "./StatCard";

function StatsCards() {
  return (
    <section className="grid gap-6 md:grid-cols-2 xl:grid-cols-4 mb-10">

      <StatCard
        icon={<FaLeaf className="text-green-700" />}
        title="Healthy Crops"
        value="92%"
        status="+2.4%"
        statusColor="text-green-600"
        iconBg="bg-green-100"
      />

      <StatCard
        icon={<FaBug className="text-red-600" />}
        title="Disease Risk"
        value="Low"
        status="Stable"
        statusColor="text-green-600"
        iconBg="bg-red-100"
      />

      <StatCard
        icon={<FaBroadcastTower className="text-blue-600" />}
        title="Active Sensors"
        value="24"
        status="Online"
        statusColor="text-blue-600"
        iconBg="bg-blue-100"
      />

      <StatCard
        icon={<FaCloudSun className="text-green-700" />}
        title="Weather"
        value="28°C"
        status="Humidity 68%"
        statusColor="text-gray-500"
        iconBg="bg-green-100"
      />

    </section>
  );
}

export default StatsCards;