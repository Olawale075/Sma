import { useState } from "react";
import { Link, NavLink } from "react-router-dom";
import {
  FaBars,
  FaTimes,
} from "react-icons/fa";

const navLinks = [
  { name: "Home", path: "/" },
  { name: "Features", path: "#features" },
  { name: "Dashboard", path: "/dashboard" },
  { name: "How It Works", path: "#how-it-works" },
  { name: "About", path: "/about" },
  // { name: "Contact", path: "/contact" },
];

function Header() {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <header className="fixed top-0 left-0 z-50 w-full bg-[#FFFFFF99] shadow-sm backdrop-blur-md">
      
      <div className="mx-auto flex h-20 max-w-7xl items-center justify-between px-6 lg:px-10">

        {/* Logo */}
        <Link
          to="/"
          onClick={() => setMenuOpen(false)}
          className="text-4xl font-extrabold text-green-700"
        >
          AgriAI
        </Link>

        {/* Desktop Navigation */}
        <nav className="hidden items-center gap-8 lg:flex">
          {navLinks.map((item) =>
            item.path.startsWith("#") ? (
              <a
                key={item.name}
                href={item.path}
                className="font-semibold text-[#40493D] transition hover:text-green-700"
              >
                {item.name}
              </a>
            ) : (
              <NavLink
                key={item.name}
                to={item.path}
                className={({ isActive }) =>
                  isActive
                    ? "font-semibold text-[#0D631B]"
                    : "font-semibold text-[#40493D] transition hover:text-green-700"
                }
              >
                {item.name}
              </NavLink>
            )
          )}
        </nav>

        {/* Desktop Buttons */}
        <div className="hidden items-center gap-4 lg:flex">
          <Link
            to="/login"
            className="font-medium text-[#0D631B] hover:text-green-900"
          >
            Login
          </Link>

          <Link
            to="/signup"
            className="rounded-full bg-green-700 px-5 py-2 text-white transition hover:bg-green-800"
          >
            Sign Up
          </Link>
        </div>

        {/* Mobile Menu Button */}
        <button
          onClick={() => setMenuOpen(!menuOpen)}
          className="text-2xl text-green-700 lg:hidden"
          aria-label="Toggle menu"
        >
          {menuOpen ? <FaTimes /> : <FaBars />}
        </button>

      </div>

      {/* ================= MOBILE MENU ================= */}
      {menuOpen && (
        <div className="border-t border-green-100 bg-white shadow-lg lg:hidden">

          <nav className="flex flex-col px-6 py-5">

            {navLinks.map((item) =>
              item.path.startsWith("#") ? (
                <a
                  key={item.name}
                  href={item.path}
                  onClick={() => setMenuOpen(false)}
                  className="border-b border-gray-100 py-4 font-semibold text-[#40493D] transition hover:text-green-700"
                >
                  {item.name}
                </a>
              ) : (
                <NavLink
                  key={item.name}
                  to={item.path}
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) =>
                    `border-b border-gray-100 py-4 font-semibold transition ${
                      isActive
                        ? "text-green-700"
                        : "text-[#40493D] hover:text-green-700"
                    }`
                  }
                >
                  {item.name}
                </NavLink>
              )
            )}

            {/* Mobile Buttons */}
            <div className="flex flex-col gap-3 pt-5">

              <Link
                to="/login"
                onClick={() => setMenuOpen(false)}
                className="rounded-lg border border-green-700 py-3 text-center font-semibold text-green-700"
              >
                Login
              </Link>

              <Link
                to="/signup"
                onClick={() => setMenuOpen(false)}
                className="rounded-lg bg-green-700 py-3 text-center font-semibold text-white transition hover:bg-green-800"
              >
                Sign Up
              </Link>

            </div>

          </nav>

        </div>
      )}

    </header>
  );
}

export default Header;