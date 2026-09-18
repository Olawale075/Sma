import Header from "../components/layout/Header";
import {
  FaEnvelope,
  FaPhone,
  FaMapMarkerAlt,
  FaPaperPlane,
} from "react-icons/fa";

function Contact() {
  return (
    <>
    <Header />
    <div className="min-h-screen bg-[#F4FAF6] text-gray-800">

      {/* Hero */}
      <section className="px-6 py-20 text-center md:py-24">
        <p className="mb-3 text-sm font-semibold uppercase tracking-widest text-green-700">
          Contact Us
        </p>

        <h1 className="mx-auto max-w-2xl text-4xl font-extrabold text-gray-900 md:text-5xl">
          Let's Talk About{" "}
          <span className="text-green-700">AgriAI</span>
        </h1>

        <p className="mx-auto mt-5 max-w-xl leading-7 text-gray-600">
          Have a question, suggestion, or need help with AgriAI? Send us a
          message and we'll be happy to hear from you.
        </p>
      </section>

      {/* Contact Section */}
      <section className="px-6 pb-20">
        <div className="mx-auto grid max-w-5xl gap-8 md:grid-cols-2">

          {/* Contact Information */}
          <div className="rounded-2xl bg-green-700 p-8 text-white">
            <h2 className="text-2xl font-bold">
              Get in Touch
            </h2>

            <p className="mt-3 leading-6 text-green-50">
              We are here to help you learn more about intelligent farming
              and the AgriAI system.
            </p>

            <div className="mt-8 space-y-6">

              <div className="flex items-center gap-4">
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-white/10">
                  <FaEnvelope />
                </div>

                <div>
                  <p className="text-sm text-green-100">
                    Email
                  </p>
                  <p className="font-medium">
                    support@agriai.com
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-4">
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-white/10">
                  <FaPhone />
                </div>

                <div>
                  <p className="text-sm text-green-100">
                    Phone
                  </p>
                  <p className="font-medium">
                    +234 800 000 0000
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-4">
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-white/10">
                  <FaMapMarkerAlt />
                </div>

                <div>
                  <p className="text-sm text-green-100">
                    Location
                  </p>
                  <p className="font-medium">
                    Nigeria
                  </p>
                </div>
              </div>

            </div>
          </div>

          {/* Contact Form */}
          <div className="rounded-2xl border border-green-100 bg-white p-8 shadow-sm">
            <h2 className="text-2xl font-bold text-gray-900">
              Send a Message
            </h2>

            <form className="mt-6 space-y-5">

              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">
                  Full Name
                </label>

                <input
                  type="text"
                  placeholder="Enter your name"
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 outline-none transition focus:border-green-600 focus:ring-2 focus:ring-green-100"
                />
              </div>

              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">
                  Email Address
                </label>

                <input
                  type="email"
                  placeholder="Enter your email"
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 outline-none transition focus:border-green-600 focus:ring-2 focus:ring-green-100"
                />
              </div>

              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">
                  Message
                </label>

                <textarea
                  rows="5"
                  placeholder="Write your message..."
                  className="w-full resize-none rounded-xl border border-gray-200 px-4 py-3 outline-none transition focus:border-green-600 focus:ring-2 focus:ring-green-100"
                ></textarea>
              </div>

              <button
                type="submit"
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-green-700 py-3 font-semibold text-white transition hover:bg-green-800"
              >
                Send Message
                <FaPaperPlane />
              </button>

            </form>
          </div>

        </div>
      </section>
    </div>
    </>
  );
}

export default Contact;
