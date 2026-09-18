import FeatureCard from "./FeatureCard";

import {
  FaRobot,
  FaBroadcastTower,
  FaCloudSunRain,
  FaBell,
  FaLeaf,
  FaChartLine,
} from "react-icons/fa";

const features = [
  {
    icon: <FaRobot />,
    title: "AI Disease Detection",
    description:
      "Computer vision models detect crop diseases from leaf images with high accuracy.",
  },
  {
    icon: <FaBroadcastTower />,
    title: "IoT Monitoring",
    description:
      "Monitor soil moisture, humidity, pH, and temperature in real time using IoT sensors.",
  },
  {
    icon: <FaCloudSunRain />,
    title: "Weather Intelligence",
    description:
      "Use weather forecasts to predict disease outbreaks before they occur.",
  },
  {
    icon: <FaBell />,
    title: "Smart Alerts",
    description:
      "Receive instant notifications when crops require immediate attention.",
  },
  {
    icon: <FaLeaf />,
    title: "Automated Intervention",
    description:
      "Generate recommendations for irrigation, pesticides, and preventive actions.",
  },
  {
    icon: <FaChartLine />,
    title: "Analytics Dashboard",
    description:
      "Visualize crop health, sensor data, and AI predictions through interactive charts.",
  },
];

function Features() {
  return (
    <section id="features" className="py-24 bg-gray-50">
      <div className="max-w-7xl mx-auto px-6 lg:px-10">

        {/* Section Title */}

        <div className="text-center mb-16">

          <h2 className="text-4xl font-bold text-green-700">
            Unmatched Field Intelligence
          </h2>

          <p className="mt-4 text-gray-600 max-w-2xl mx-auto">
            Equipping farmers with intelligent tools to monitor crops,
            predict diseases, and improve agricultural productivity.
          </p>

        </div>

        {/* Cards */}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">

          {features.map((feature) => (
            <FeatureCard
              key={feature.title}
              icon={feature.icon}
              title={feature.title}
              description={feature.description}
            />
          ))}

        </div>

      </div>
    </section>
  );
}

export default Features;