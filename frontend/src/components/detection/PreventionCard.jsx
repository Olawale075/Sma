import { FaCheckCircle } from "react-icons/fa";

function PreventionCard(){

    const tips=[

        "Rotate with a non-host crop next season",

        "Improve row spacing to increase airflow",

        "Remove and destroy infected crop residue"

    ]

    return(

        <div className="border rounded-2xl p-5 mt-6 border-gray-500">

            <h3 className="font-bold text-xl mb-5">

                Preventive Measures

            </h3>

            <div className="space-y-4">

                {tips.map((tip)=>(

                    <div key={tip} className="flex items-center gap-3">

                        <FaCheckCircle className="text-green-600"/>

                        <span>{tip}</span>

                    </div>

                ))}

            </div>

        </div>

    )

}

export default PreventionCard;