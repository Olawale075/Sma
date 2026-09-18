function SeverityCard({

    title="Severity",

    body="Moderate — 18% leaf area affected"

}) {

    return (

        <div className="border rounded-2xl p-5 border-gray-500">

            <h3 className="text-gray-500 uppercase text-sm">

                {title}

            </h3>

            <p className="font-semibold text-lg mt-2">

                {body}

            </p>

        </div>

    )

}

export default SeverityCard;