import AuthLeft from "./AuthLeft";

function AuthLayout({ children }) {
  return (
    <div className="min-h-screen bg-[#EAF3FB]">

      <div className="grid min-h-screen lg:grid-cols-2">

        <AuthLeft />

        <div className="flex items-center justify-center px-6 py-10">

          {children}

        </div>

      </div>

    </div>
  );
}

export default AuthLayout;