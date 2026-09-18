import {
  FaBroadcastTower,
  FaCloud,
  FaBrain,
  FaChartLine,
  FaBell,
} from "react-icons/fa";
import Farm2 from "../assets/farm2.png";

const steps = [
  {
    icon: <FaBroadcastTower />,
    title: "Sensors",
    description:
      "Hardened IoT nodes collect environmental data.",
  },
  {
    icon: <FaCloud />,
    title: "Cloud",
    description:
      "Encrypted telemetry streams to secure servers.",
  },
  {
    icon: <FaBrain />,
    title: "AI Engine",
    description:
      "Neural networks analyze patterns and anomalies.",
    active: true,
  },
  {
    icon: <FaChartLine />,
    title: "Prediction",
    description:
      "Future risks are calculated with temporal precision.",
  },
  {
    icon: <FaBell />,
    title: "Notification",
    description:
      "Actionable insights delivered instantly.",
  },
];

function HowItWorks() {
  return (
    <section id="how-it-works" className="py-24 bg-white">

      <div className="max-w-7xl mx-auto px-6">

        <div className="text-center mb-16">

          <h2 className="text-4xl font-bold text-green-700">
            The AgriAI Ecosystem
          </h2>

          <p className="text-gray-500 mt-2">
            From soil to signal: How we secure your harvest.
          </p>

        </div>

        <div className="grid grid-cols-2 md:grid-cols-5 gap-8 text-center relative">

          {steps.map((step) => (

            <div key={step.title}>

              <div
                className={`w-16 h-16 rounded-full mx-auto flex items-center justify-center text-2xl border-2
                ${
                  step.active
                    ? "bg-green-700 text-white border-green-700"
                    : "border-green-700 text-green-700 bg-white"
                }`}
              >
                {step.icon}
              </div>

              <h3 className="font-semibold mt-4">
                {step.title}
              </h3>

              <p className="text-sm text-gray-500 mt-2">
                {step.description}
              </p>

            </div>

          ))}

        </div>

        <div className="mt-20 rounded-3xl overflow-hidden shadow-xl">

          <img
            src={Farm2}
            alt="Smart Farm"
            className="w-full h-[450px] object-cover"
          />

        </div>

      </div>
    </section>
  );
}

export default HowItWorks;