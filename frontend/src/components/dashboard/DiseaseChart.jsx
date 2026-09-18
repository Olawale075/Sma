import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

const data = [
  { day: "Mon", risk: 20 },
  { day: "Tue", risk: 35 },
  { day: "Wed", risk: 28 },
  { day: "Thu", risk: 55 },
  { day: "Fri", risk: 40 },
  { day: "Sat", risk: 25 },
  { day: "Sun", risk: 18 },
];

function DiseaseChart() {
  return (
    <div className="bg-white rounded-2xl shadow-sm p-6">

      <h2 className="text-xl font-bold mb-5">
        Disease Prediction Trend
      </h2>

      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={data}>
          <XAxis dataKey="day" />
          <YAxis />
          <Tooltip />
          <Line
            type="monotone"
            dataKey="risk"
            stroke="#16a34a"
            strokeWidth={3}
          />
        </LineChart>
      </ResponsiveContainer>

    </div>
  );
}

export default DiseaseChart;