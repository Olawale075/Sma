import farmImage from "../../assets/farm.png";

function AuthLeft() {
  return (
    <div
      className="relative hidden lg:flex bg-cover bg-center"
      style={{
        backgroundImage: `url(${farmImage})`,
      }}
    >
      {/* Overlay */}

      <div className="absolute inset-0 bg-black/35"></div>

      {/* Floating Card */}

      {/* <div className="absolute top-10 right-10 w-56 rounded-3xl bg-white/90 p-5 shadow-xl backdrop-blur">

        <div className="text-center">

          <p className="text-xs uppercase tracking-widest text-gray-500">
            Ecosystem v2.4
          </p>

          <h2 className="mt-2 text-2xl font-bold text-green-700">
            AgriAI
          </h2>

          <p className="mt-2 text-sm text-gray-500">
            Sign in to monitor your farm
          </p>

          <div className="mt-5 space-y-3">

            <div className="h-10 rounded-lg bg-gray-100"></div>

            <div className="h-10 rounded-lg bg-gray-100"></div>

            <div className="h-10 rounded-lg bg-green-600"></div>

          </div>

        </div>

      </div> */}

      {/* Bottom Content */}

      <div className="relative z-10 mt-auto p-10 text-white">

        <h1 className="text-6xl font-extrabold">
          AgriAI
        </h1>

        <p className="mt-5 max-w-lg text-lg leading-8">

          Harness the power of precision agriculture.
          Join thousands of farmers using Artificial Intelligence
          and IoT sensors to improve crop health and increase yield.

        </p>

        <div className="mt-10 flex gap-5">

          <div className="rounded-full bg-white/20 px-6 py-3 backdrop-blur">

            🟢 Live Data Stream

          </div>

          <div className="rounded-full bg-white/20 px-6 py-3 backdrop-blur">

            📡 42.4K Sensors Active

          </div>

        </div>

      </div>

    </div>
  );
}

export default AuthLeft;