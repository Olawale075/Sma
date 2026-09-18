import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from "react";

const CameraStream = forwardRef(function CameraStream(
  { setSensorConnected, connectRequest },
  ref
) {
  const canvasRef = useRef(null);
  const socketRef = useRef(null);

  const [connected, setConnected] = useState(false);
  const [status, setStatus] = useState("Disconnected");
  const [frameCount, setFrameCount] = useState(0);

  // SERVER CONFIGURATION

  const SERVER_HOST = "crop-disease-detector-8nqt.onrender.com";
  const SERVER_PORT = "443";

  const WS_URL =
    `wss://${SERVER_HOST}:${SERVER_PORT}/camera-stream`;

  
  // CONNECT
  function connect() {
    const existingSocket = socketRef.current;

    // Already connected
    if (
      existingSocket &&
      existingSocket.readyState === WebSocket.OPEN
    ) {
      console.log("Already connected");
      return;
    }

    // Already connecting
    if (
      existingSocket &&
      existingSocket.readyState === WebSocket.CONNECTING
    ) {
      console.log("Connection already in progress");
      return;
    }

    console.log("Connecting to:", WS_URL);

    setStatus("Connecting...");

    const socket = new WebSocket(WS_URL);

    socket.binaryType = "blob";


    // CONNECTED


    socket.onopen = () => {
      console.log("WebSocket connected");

      setConnected(true);
      setStatus("Connected");

      // Tell Sensors.jsx
      setSensorConnected(true);

      // Register web client
      const registerMessage = {
        type: "register",
        deviceId: "web-client",
        deviceType: "Web Browser",
        location: "Local",
        cropType: "Cassava Leaf",
        timestamp: Date.now(),
      };

      socket.send(JSON.stringify(registerMessage));

      console.log("Registration sent");

      // Request camera stream
      setTimeout(() => {
        if (socket.readyState === WebSocket.OPEN) {
          socket.send(
            JSON.stringify({
              command: "start_stream",
              timestamp: Date.now(),
            })
          );

          console.log("Stream requested");
        }
      }, 1000);
    };


    // RECEIVE DATA


    socket.onmessage = (event) => {
      // Camera frame
      if (event.data instanceof Blob) {
        displayFrame(event.data);
      } else {
        // console.log("Server message:", event.data);

        try {
          const data = JSON.parse(event.data);

          // console.log("Parsed server message:", data);

          if (data.type === "command_response") {
            console.log(
              `Command ${data.command}: ${data.status}`
            );
          }

          if (
            data.type === "system" &&
            data.status === "registered"
          ) {
            console.log("Device registration successful");
          }
        } catch {
          console.log("Server text:", event.data);
        }
      }
    };


    // DISCONNECTED


    socket.onclose = (event) => {
      console.log(
        "WebSocket disconnected:",
        event.code,
        event.reason
      );

      setConnected(false);
      setStatus("Disconnected");
      setSensorConnected(false);

      socketRef.current = null;
    };


    // ERROR


    socket.onerror = (error) => {
      console.error("WebSocket error:", error);

      setConnected(false);
      setStatus("Error");
      setSensorConnected(false);
    };

    socketRef.current = socket;
  }

  // DISCONNECT

  function disconnect() {
    console.log("Disconnecting sensor...");

    const socket = socketRef.current;

    if (!socket) {
      setConnected(false);
      setStatus("Disconnected");
      setSensorConnected(false);
      return;
    }

    // Tell server to stop streaming
    if (socket.readyState === WebSocket.OPEN) {
      socket.send(
        JSON.stringify({
          command: "stop_stream",
          timestamp: Date.now(),
        })
      );
    }

    // Close WebSocket
    socket.close(1000, "User disconnected");

    socketRef.current = null;

    setConnected(false);
    setStatus("Disconnected");
    setSensorConnected(false);
  }

  // MAKE CONNECT / DISCONNECT AVAILABLE TO SIDEBAR

  useImperativeHandle(ref, () => ({
    connect,
    disconnect,
  }));

  // AUTOMATIC CONNECTION

  useEffect(() => {
    connect();

    return () => {
      if (socketRef.current) {
        socketRef.current.close();
        socketRef.current = null;
      }
    };
  }, []);

  // MANUAL CONNECT REQUEST

  useEffect(() => {
    if (connectRequest > 0) {
      connect();
    }
  }, [connectRequest]);

  // DISPLAY CAMERA FRAME

  function displayFrame(blob) {
    const canvas = canvasRef.current;

    if (!canvas) return;

    const ctx = canvas.getContext("2d");

    const imageUrl = URL.createObjectURL(blob);

    const image = new Image();

    image.onload = () => {
      ctx.clearRect(
        0,
        0,
        canvas.width,
        canvas.height
      );

      ctx.drawImage(
        image,
        0,
        0,
        canvas.width,
        canvas.height
      );

      setFrameCount(
        (previous) => previous + 1
      );

      URL.revokeObjectURL(imageUrl);
    };

    image.onerror = () => {
      console.error("Failed to decode camera frame");

      URL.revokeObjectURL(imageUrl);
    };

    image.src = imageUrl;
  }

  // UI

  return (
    <div className="mt-8 rounded-2xl bg-white p-5 shadow-sm">

      {/* Header */}

      <div className="mb-4 flex items-center justify-between">

        <div>
          <h2 className="text-lg font-semibold text-gray-900">
            Live Camera Stream
          </h2>

          <p className="text-sm text-gray-500">
            ESP32-CAM disease monitoring
          </p>
        </div>

        {/* Connection status */}

        <div
          className={`rounded-full px-3 py-1 text-sm ${
            connected
              ? "bg-green-100 text-green-700"
              : "bg-red-100 text-red-700"
          }`}
        >
          ● {status}
        </div>

      </div>

      {/* Camera */}

      <div className="relative overflow-hidden rounded-xl bg-black">

        <canvas
          ref={canvasRef}
          width={1280}
          height={720}
          className="h-auto w-full"
        />

        {/* Stream label */}

        <div className="absolute left-4 top-4 rounded-lg bg-black/70 px-3 py-2 text-sm text-white">
          {connected
            ? "● LIVE"
            : "● OFFLINE"}
        </div>

      </div>

      {/* Statistics */}

      <div className="mt-4 flex flex-wrap gap-6 text-sm text-gray-600">

        <p>
          Frames:
          <span className="ml-1 font-semibold text-gray-900">
            {frameCount}
          </span>
        </p>

        <p className="break-all">
          Server:
          <span className="ml-1 font-semibold text-gray-900">
            {SERVER_HOST}:{SERVER_PORT}
          </span>
        </p>

      </div>

    </div>
  );
});

export default CameraStream;