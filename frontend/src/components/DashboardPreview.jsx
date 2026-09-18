import { FaCheckCircle } from "react-icons/fa";
import Computer from "../assets/computer.png";

function DashboardPreview() {
  return (
    <section className="bg-sky-50 py-24">

      <div className="max-w-7xl mx-auto px-6 grid lg:grid-cols-2 gap-16 items-center">

        <div className="relative">
          <img
            src={Computer}
            alt="Computer dashboard"
            className="rounded-3xl shadow-lg"
          />

        </div>

        <div>

          <span className="uppercase text-green-700 font-bold tracking-widest">
            Enterprise Dashboard
          </span>

          <h2 className="text-5xl font-bold mt-4 text-gray-800">
            Control your entire operation from one screen.
          </h2>

          <p className="text-gray-500 mt-6 leading-8">
            Our dashboard gives you complete visibility into crop health,
            sensors, disease predictions and environmental conditions.
          </p>

          <div className="mt-10 space-y-5">

            <div className="flex items-center gap-3">
              <FaCheckCircle className="text-green-700" />
              Real-time IoT stream integration
            </div>

            <div className="flex items-center gap-3">
              <FaCheckCircle className="text-green-700" />
              Yield forecasting using AI
            </div>

            <div className="flex items-center gap-3">
              <FaCheckCircle className="text-green-700" />
              Customizable alert thresholds
            </div>

          </div>

        </div>

      </div>

    </section>
  );
}

export default DashboardPreview;