function AIInsight() {
  return (
    <div className="bg-green-50 rounded-2xl shadow-sm p-6 mt-6">

      <h2 className="text-xl font-bold text-green-700">
        AgriAI Insight
      </h2>

      <p className="text-gray-600 mt-4 leading-8">
        Based on current soil moisture (22%) and
        weather forecast, we recommend starting
        drip irrigation in Sector B before
        6:00 PM to improve nutrient absorption.
      </p>

      <button className="mt-8 bg-green-700 text-white px-5 py-3 rounded-xl hover:bg-green-800 transition">
        Execute Recommendation
      </button>

    </div>
  );
}

export default AIInsight;