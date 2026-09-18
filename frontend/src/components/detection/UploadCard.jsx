import { useRef, useState } from "react";
import { FaUpload, FaCamera } from "react-icons/fa";

function UploadCard() {
  const fileInputRef = useRef(null);
  const cameraInputRef = useRef(null);

  const [selectedImage, setSelectedImage] = useState(null);

  const handleFileChange = (e) => {
    const file = e.target.files[0];

    if (!file) return;

    setSelectedImage(URL.createObjectURL(file));

    console.log(file);
  };

  return (
    <div className="rounded-3xl bg-white p-6 shadow-sm border border-gray-200">

      <h2 className="text-2xl font-bold mb-6">
        Image Upload
      </h2>

      <div className="border-2 border-dashed border-gray-300 rounded-3xl py-16 flex flex-col items-center">

        {selectedImage ? (
          <img
            src={selectedImage}
            alt="Preview"
            className="w-72 rounded-xl mb-6"
          />
        ) : (
          <>
            <FaUpload className="text-5xl text-green-700 mb-5" />

            <h3 className="text-xl font-semibold">
              Drag & Drop Crop Images
            </h3>

            <p className="text-gray-500 mt-2">
              JPG or PNG • Max 10MB
            </p>
          </>
        )}

        <div className="flex flex-col xl:flex-row gap-4 mt-8">

          <button
            onClick={() => fileInputRef.current.click()}
            className="bg-green-700 text-white px-8 py-3 rounded-xl hover:bg-green-800"
          >
            Browse Files
          </button>

          <button
            onClick={() => cameraInputRef.current.click()}
            className="border border-gray-300 px-8 py-3 rounded-xl flex items-center gap-3 hover:bg-gray-100"
          >
            <FaCamera />
            Camera Upload
          </button>

        </div>

        {/* Browse File Input */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          onChange={handleFileChange}
          className="hidden"
        />

        {/* Camera Input */}
        <input
          ref={cameraInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          onChange={handleFileChange}
          className="hidden"
        />

      </div>

      <p className="mt-6 text-gray-500">
        Tip: Capture the affected leaf in daylight filling most of the frame.
      </p>

    </div>
  );
}

export default UploadCard;