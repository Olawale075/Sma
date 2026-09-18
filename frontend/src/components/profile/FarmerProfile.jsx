function FarmerProfile() {
    const [sidebarOpen, setSidebarOpen] = useState(false);

    <div className="mb-10 flex flex-col justify-between gap-6 md:flex-row md:items-center">

        <div>

            <h1 className="text-5xl font-bold text-gray-800">
            Farmer Profile
            </h1>

            <p className="mt-3 text-lg text-gray-500">
            Account and farm information used to personalise predictions.
            </p>

        </div>

        <button className="rounded-xl border border-gray-200 bg-white px-6 py-3 shadow-sm hover:bg-gray-50">
            Edit Profile
        </button>

    </div>

}