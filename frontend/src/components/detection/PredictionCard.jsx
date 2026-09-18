import SeverityCard from "./SeverityCard";
import TreatmentCard from "./TreatmentCard";
import PreventionCard from "./PreventionCard";

function PredictionCard() {
    return (

        <div className="rounded-3xl bg-white p-6 shadow-sm border border-gray-200">

            <div className="flex justify-between items-center">

                <h2 className="text-2xl font-bold">
                    Prediction Result
                </h2>

                <span className="bg-orange-100 text-orange-600 px-4 py-2 rounded-full font-semibold">

                    Moderate Severity

                </span>

            </div>

            <p className="text-gray-400 uppercase mt-6">

                Disease Name

            </p>

            <h1 className="text-2xl font-semibold mt-1">

                Northern Corn Leaf Blight

            </h1>

            <div className="mt-8">

                <div className="flex justify-between mb-2">

                    <span className="font-medium">

                        Confidence Score

                    </span>

                    <span className="font-bold">

                        92%

                    </span>

                </div>

                <div className="w-full h-3 bg-gray-200 rounded-full">

                    <div className="bg-green-700 h-3 rounded-full w-[92%]"/>

                </div>

            </div>

            <div className="grid md:grid-cols-2 gap-5 mt-8">

                <SeverityCard/>

                <SeverityCard
                    title="Estimated Recovery"
                    body="10–14 days with treatment"
                />

            </div>

            <TreatmentCard/>

            <PreventionCard/>

        </div>

    )
}

export default PredictionCard;