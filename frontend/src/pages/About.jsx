import { Link } from "react-router-dom";
import Header from "../components/layout/Header";
import {
  FaBrain,
  FaBroadcastTower,
  FaSeedling,
  FaArrowRight,
} from "react-icons/fa";

function About() {
  const features = [
    {
      icon: <FaBrain />,
      title: "AI Detection",
      text: "AI analyzes crop images to detect diseases quickly and accurately.",
    },
    {
      icon: <FaBroadcastTower />,
      title: "IoT Monitoring",
      text: "Sensors continuously monitor important environmental conditions on the farm.",
    },
    {
      icon: <FaSeedling />,
      title: "Early Intervention",
      text: "Get timely recommendations before crop problems become more serious.",
    },
  ];

  return (
    <>
    <Header />
    <div className="min-h-screen bg-[#F4FAF6] text-gray-800">

      {/* Hero */}
      <section className="px-6 py-24 text-center md:py-32">
        <p className="mb-3 text-sm font-semibold uppercase tracking-widest text-green-700">
          About AgriAI
        </p>

        <h1 className="mx-auto max-w-3xl text-4xl font-extrabold text-gray-900 md:text-5xl">
          Smarter Technology for{" "}
          <span className="text-green-700">Better Farming</span>
        </h1>

        <p className="mx-auto mt-6 max-w-2xl leading-7 text-gray-600">
          AgriAI combines artificial intelligence, IoT sensors, and
          environmental data to help farmers detect crop diseases early and
          make better farming decisions.
        </p>
      </section>

      {/* What We Do */}
      <section className="bg-white px-6 py-16">
        <div className="mx-auto max-w-6xl">
          <div className="mb-10 text-center">
            <h2 className="text-3xl font-bold text-gray-900">
              What AgriAI Does
            </h2>

            <p className="mt-3 text-gray-600">
              Simple technology designed to give farmers better insight.
            </p>
          </div>

          <div className="grid gap-6 md:grid-cols-3">
            {features.map((feature) => (
              <div
                key={feature.title}
                className="rounded-2xl border border-green-100 bg-[#F4FAF6] p-6"
              >
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-green-100 text-xl text-green-700">
                  {feature.icon}
                </div>

                <h3 className="mt-5 text-lg font-bold text-gray-900">
                  {feature.title}
                </h3>

                <p className="mt-2 text-sm leading-6 text-gray-600">
                  {feature.text}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Mission */}
      <section className="px-6 py-16 text-center">
        <div className="mx-auto max-w-3xl">
          <h2 className="text-3xl font-bold text-gray-900">
            Our Mission
          </h2>

          <p className="mt-4 leading-7 text-gray-600">
            Our mission is to help farmers reduce crop losses, use resources
            more efficiently, and protect their crops through intelligent
            technology.
          </p>

          <Link
            to="/signup"
            className="mt-7 inline-flex items-center gap-2 rounded-xl bg-green-700 px-6 py-3 font-semibold text-white transition hover:bg-green-800"
          >
            Get Started
            <FaArrowRight />
          </Link>
        </div>
      </section>

    </div>
    </>
  );
}

export default About;

