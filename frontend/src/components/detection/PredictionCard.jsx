import SeverityCard from "./SeverityCard";
import TreatmentCard from "./TreatmentCard";
import PreventionCard from "./PreventionCard";

function PredictionCard({ prediction }) {
    const result = prediction?.topResult || prediction?.results?.[0];
    const confidence = prediction?.confidencePercent ??
        (result?.probability != null ? Math.round(result.probability * 100) : null);
    const severity = result?.severity || "—";

    return (
        <div className="rounded-3xl bg-white p-6 shadow-sm border border-gray-200">
            <div className="flex justify-between items-center">
                <h2 className="text-2xl font-bold">
                    Prediction Result
                </h2>
                <span className="bg-orange-100 text-orange-600 px-4 py-2 rounded-full font-semibold">
                    {severity}
                </span>
            </div>
            <p className="text-gray-400 uppercase mt-6">
                {result?.healthStatus || "Analysis"}
            </p>
            <h1 className="text-2xl font-semibold mt-1">
                {result?.conditionName || result?.className || "Upload an image to get a prediction"}
            </h1>
            {prediction && (
              <>
                {prediction.summary && <p className="mt-4 text-gray-600">{prediction.summary}</p>}
                <div className="mt-8">
                <div className="flex justify-between mb-2">
                    <span className="font-medium">Confidence Score</span>
                    <span className="font-bold">{confidence ?? "—"}{confidence != null ? "%" : ""}</span>
                </div>
                <div className="w-full h-3 bg-gray-200 rounded-full">
                    <div
                      className="bg-green-700 h-3 rounded-full"
                      style={{ width: `${confidence ?? 0}%` }}
                    />
                </div>
                </div>
                <div className="grid md:grid-cols-2 gap-5 mt-8">
                    <SeverityCard body={severity} />
                    <SeverityCard title="When to act" body={result?.whenToAct || "—"} />
                </div>
                <TreatmentCard treatment={result?.treatment} />
                <PreventionCard tips={result?.prevention} />
              </>
            )}
        </div>
    )
}

export default PredictionCard;