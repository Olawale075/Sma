function FeatureCard({ icon, title, description }) {
  return (
    <div className="bg-white rounded-2xl shadow-md hover:shadow-xl transition duration-300 p-8">
      <div className="w-14 h-14 rounded-xl bg-green-100 flex items-center justify-center text-green-700 text-2xl mb-6">
        {icon}
      </div>

      <h3 className="text-xl font-bold text-gray-800 mb-3">
        {title}
      </h3>

      <p className="text-gray-500 leading-7">
        {description}
      </p>
    </div>
  );
}

export default FeatureCard;