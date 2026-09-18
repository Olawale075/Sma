import { Link } from "react-router-dom";
import {
  FaBroadcastTower,
  FaTint,
  FaSeedling,
} from "react-icons/fa";

function Hero() {
  return (
    <section className="relative min-h-screen overflow-hidden bg-gradient-to-b from-green-700 via-emerald-300 to-white">

      {/* Background glow */}
      <div className="absolute inset-0 bg-gradient-to-b from-green-800/20 via-transparent to-white/20" />

      {/* Hero content */}
      <div className="relative z-10 mx-auto flex min-h-screen max-w-7xl flex-col items-center justify-center px-6 pt-24 pb-32 text-center">

        {/* Badge */}
        <div className="mb-8 inline-flex items-center gap-2 rounded-full border border-green-700/20 bg-green-500/10 px-4 py-2 text-sm font-medium text-green-800 backdrop-blur-sm">

          <FaBroadcastTower className="text-xs" />

          <span>Next-Gen Farming Live</span>

        </div>

        {/* Heading */}
        <h1 className="max-w-5xl text-5xl font-extrabold leading-tight tracking-tight text-gray-900 sm:text-6xl md:text-7xl lg:text-8xl">

          AI-Powered{" "}

          <span className="text-green-700">
            Precision
          </span>

          <br />

          <span className="text-green-700">
            Agriculture
          </span>

        </h1>

        {/* Description */}
        <p className="mt-8 max-w-3xl text-base leading-7 text-gray-700 sm:text-lg md:text-xl">

          Predict crop diseases before they spread using Artificial
          Intelligence, IoT sensors, weather analysis, and automated
          early intervention.

        </p>

        {/* Buttons */}
        <div className="mt-10 flex w-full flex-col items-center justify-center gap-4 sm:flex-row">

          <Link
            to="/signup"
            className="w-full rounded-xl bg-green-800 px-10 py-4 font-bold text-white shadow-lg transition duration-300 hover:-translate-y-1 hover:bg-green-900 hover:shadow-xl sm:w-auto"
          >
            Get Started
          </Link>

          <Link
            to=""
            className="w-full rounded-xl border border-white/60 bg-white/50 px-10 py-4 font-bold text-green-800 shadow-sm backdrop-blur-md transition duration-300 hover:-translate-y-1 hover:bg-white/70 sm:w-auto"
          >
            Learn More
          </Link>

        </div>

        {/* Status Cards */}
        <div className="absolute bottom-8 left-1/2 flex w-full -translate-x-1/2 justify-center px-6">

          <div className="flex flex-col gap-4 sm:flex-row">

            {/* Moisture */}
            <div className="flex items-center gap-4 rounded-xl border border-white/60 bg-white/70 px-6 py-4 shadow-md backdrop-blur-md">

              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 text-green-700">
                <FaTint />
              </div>

              <div className="text-left">

                <p className="text-sm text-gray-600">
                  Moisture Level
                </p>

                <p className="font-bold text-green-800">
                  64% - Optimal
                </p>

              </div>

            </div>

            {/* Plant Health */}
            <div className="flex items-center gap-4 rounded-xl border border-white/60 bg-white/70 px-6 py-4 shadow-md backdrop-blur-md">

              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 text-green-700">
                <FaSeedling />
              </div>

              <div className="text-left">

                <p className="text-sm text-gray-600">
                  Plant Health
                </p>

                <p className="font-bold text-green-800">
                  98% High Alert
                </p>

              </div>

            </div>

          </div>

        </div>

      </div>

    </section>
  );
}

export default Hero;