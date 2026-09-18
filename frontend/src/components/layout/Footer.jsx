import { FaFacebook, FaTwitter, FaLinkedin } from "react-icons/fa";

function Footer() {
  return (
    <footer className="bg-gray-200 py-10">

      <div className="max-w-7xl mx-auto px-6 flex flex-col lg:flex-row justify-between items-center gap-8">

        <div>

          <h2 className="text-4xl font-bold text-green-700">
            AgriAI
          </h2>

          <p className="text-gray-600 mt-2">
            Revolutionizing the way the world grows food through
            intelligent agriculture.
          </p>

        </div>

        <div className="flex gap-8 text-gray-600">

          <a href="#">Documentation</a>

          <a href="#">Research</a>

          <a href="#">Privacy Policy</a>

          <a href="#">Terms</a>

        </div>

        <div className="flex gap-4 text-green-700 text-xl">

          <FaFacebook />

          <FaTwitter />

          <FaLinkedin />

        </div>

      </div>

    </footer>
  );
}

export default Footer;