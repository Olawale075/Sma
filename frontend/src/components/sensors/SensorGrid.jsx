import {
  FaTemperatureHigh,
  FaTint,
  FaWater,
  FaFlask,
  FaSun,
  FaWind,
} from "react-icons/fa";

import SensorCard from "./SensorCard";

const sensors = [
  {
    title: "Ambient Temperature",
    value: "24",
    unit: "°C",
    icon: <FaTemperatureHigh />,
    footerLeft: "Range: 18° - 32°",
    footerRight: "+2%",
    status: "LIVE",
  },
  {
    title: "Air Humidity",
    value: "65",
    unit: "%",
    icon: <FaTint />,
    footerLeft: "Target: 60 - 70%",
    footerRight: "Optimal",
    status: "LIVE",
  },
  {
    title: "Soil Moisture",
    value: "42",
    unit: "%",
    icon: <FaWater />,
    footerLeft: "Critical: <20%",
    footerRight: "Check Pump",
    status: "LIVE",
  },
  // {
  //   title: "Soil pH",
  //   value: "6.5",
  //   unit: "",
  //   icon: <FaFlask />,
  //   footerLeft: "Status: Slightly Acidic",
  //   footerRight: "Balanced",
  //   status: "LIVE",
  // },
  // {
  //   title: "Light Exposure",
  //   value: "12",
  //   unit: "k lux",
  //   icon: <FaSun />,
  //   footerLeft: "Peak: 2:00 PM",
  //   footerRight: "Strong",
  //   status: "LIVE",
  // },
  // {
  //   title: "Wind Speed",
  //   value: "8",
  //   unit: "km/h",
  //   icon: <FaWind />,
  //   footerLeft: "Direction: North-East",
  //   footerRight: "Gentle",
  //   status: "LIVE",
  // },
];

function SensorGrid() {
  return (
    <section className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
      {sensors.map((sensor) => (
        <SensorCard
          key={sensor.title}
          icon={sensor.icon}
          title={sensor.title}
          value={sensor.value}
          unit={sensor.unit}
          footerLeft={sensor.footerLeft}
          footerRight={sensor.footerRight}
          status={sensor.status}
        />
      ))}
    </section>
  );
}

export default SensorGrid;