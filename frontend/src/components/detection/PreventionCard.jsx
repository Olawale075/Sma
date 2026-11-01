import { FaCheckCircle } from "react-icons/fa";

function PreventionCard({ tips = [] }){

    return(

        <div className="border rounded-2xl p-5 mt-6 border-gray-500">

            <h3 className="font-bold text-xl mb-5">

                Preventive Measures

            </h3>

            <div className="space-y-4">
                {tips.length ? tips.map((tip)=>(

                    <div key={tip} className="flex items-center gap-3">

                        <FaCheckCircle className="text-green-600"/>

                        <span>{tip}</span>

                    </div>

                )) : <p className="text-gray-600">No preventive measures returned.</p>}

            </div>

        </div>

    )

}

export default PreventionCard;