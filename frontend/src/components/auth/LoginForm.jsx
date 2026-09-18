import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  FaGoogle,
  FaMicrosoft,
  FaEye,
  FaEyeSlash,
  FaArrowRight,
} from "react-icons/fa";
import { login } from "../../services/auth";

function LoginForm() {
  const navigate = useNavigate();

  const [phonenumber, setPhonenumber] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();

    setError("");

    if (!phonenumber || !password) {
      setError("Please enter your phone number and password.");
      return;
    }

    try {
      setLoading(true);

      const data = await login(phonenumber, password);

      console.log(" LOGIN RESPONSE ");
      // console.log(data);

      navigate("/dashboard");
    } catch (error) {
      console.error("Login error:", error);
      setError(error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-xl rounded-3xl bg-white p-8 shadow-xl">

      <h1 className="text-4xl font-bold text-gray-800">
        Welcome Back
      </h1>

      <p className="mt-2 text-gray-500">
        Login to continue monitoring your farm.
      </p>

      <form onSubmit={handleSubmit} className="mt-8 space-y-5">

        {/* Error */}

        {error && (
          <div className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600">
            {error}
          </div>
        )}

        {/* Phone Number */}

        <div>
          <label className="mb-2 block text-sm font-semibold text-gray-700">
            Phone Number
          </label>

          <input
            type="tel"
            value={phonenumber}
            onChange={(e) => setPhonenumber(e.target.value)}
            placeholder="09039731207"
            className="w-full rounded-xl border border-gray-300 px-4 py-3 outline-none transition focus:border-green-600"
          />
        </div>

        {/* Password */}

        <div>
          <div className="mb-2 flex justify-between">

            <label className="text-sm font-semibold text-gray-700">
              Password
            </label>

            <button
              type="button"
              className="text-sm text-green-700 hover:underline"
            >
              Forgot Password?
            </button>

          </div>

          <div className="relative">

            <input
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="********"
              className="w-full rounded-xl border border-gray-300 px-4 py-3 pr-12 outline-none transition focus:border-green-600"
            />

            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-500"
            >
              {showPassword ? <FaEyeSlash /> : <FaEye />}
            </button>

          </div>
        </div>

        {/* Remember */}

        <div className="flex items-center justify-between">

          <label className="flex items-center gap-2 text-sm text-gray-600">
            <input type="checkbox" />
            Remember me
          </label>

        </div>

        {/* Login Button */}

        <button
          type="submit"
          disabled={loading}
          className="flex w-full items-center justify-center gap-2 rounded-xl bg-green-700 py-4 font-semibold text-white transition hover:scale-[1.02] hover:bg-green-800 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {loading ? "Logging in..." : "Login"}

          {!loading && <FaArrowRight />}
        </button>

      </form>

      {/* Divider */}

      <div className="my-8 flex items-center">

        <div className="h-px flex-1 bg-gray-300"></div>

        <span className="mx-4 text-sm text-gray-500">
          OR CONTINUE WITH
        </span>

        <div className="h-px flex-1 bg-gray-300"></div>

      </div>

      {/* Social */}

      <div className="grid grid-cols-2 gap-4">

        <button
          type="button"
          className="flex items-center justify-center gap-2 rounded-xl border border-gray-300 py-3 hover:bg-gray-50"
        >
          <FaGoogle className="text-red-500" />
          Google
        </button>

        <button
          type="button"
          className="flex items-center justify-center gap-2 rounded-xl border border-gray-300 py-3 hover:bg-gray-50"
        >
          <FaMicrosoft className="text-blue-500" />
          Microsoft
        </button>

      </div>

      {/* Signup */}

      <p className="mt-8 text-center text-gray-600">

        Don't have an account?

        <Link
          to="/signup"
          className="ml-2 font-semibold text-green-700 hover:underline"
        >
          Create one
        </Link>

      </p>

      {/* Footer */}

      <div className="mt-10 flex justify-center gap-8 text-sm text-gray-500">

        <button className="hover:text-green-700">
          Privacy Policy
        </button>

        <button className="hover:text-green-700">
          Security Standards
        </button>

      </div>

    </div>
  );
}

export default LoginForm;