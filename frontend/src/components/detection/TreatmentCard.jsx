function TreatmentCard({ treatment }){

    return(

        <div className="border rounded-2xl p-5 mt-6 border-gray-500">

            <h3 className="font-bold text-xl mb-3">

                Recommended Treatment

            </h3>

            <p className="text-gray-600 leading-8">{treatment || "No treatment recommendation returned."}</p>

        </div>

    )

}

export default TreatmentCard;