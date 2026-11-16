import { useEffect, useState } from "react";

const API_URL = (import.meta.env.VITE_API_URL || "https://fireeyes-detector-ib01.onrender.com")
  .replace(/\/+$/, "");

function PaymentReturnNotice() {
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const reference = params.get("transactionReference") || params.get("ref");
    if (!reference) return;

    const callbackStatus = params.get("paymentStatus")?.toLowerCase();
    let cancelled = false;
    let timeoutId;

    async function checkPayment() {
      const authToken = localStorage.getItem("token");

      async function refreshBalance() {
        const storedUser = localStorage.getItem("user");
        const userProfile = storedUser ? JSON.parse(storedUser) : {};
        const phoneNumber = userProfile.phoneNumber
          || userProfile.phoneNumbers
          || userProfile.phonenumber;
        if (!phoneNumber) {
          throw new Error("Payment verified, but your saved user profile is unavailable.");
        }

        const userResponse = await fetch(
          `${API_URL}/user/${encodeURIComponent(phoneNumber)}`,
          { headers: { Authorization: `Bearer ${authToken}` } },
        );
        const user = await userResponse.json();
        if (!userResponse.ok) {
          throw new Error(user.message || `Could not refresh your token balance (HTTP ${userResponse.status}).`);
        }

        localStorage.setItem("user", JSON.stringify(user));
        window.dispatchEvent(new Event("userUpdated"));
        if (!cancelled) {
          setMessage(
            `Payment verified. Updated balances: AI ${user.AITokenBalance}, SMS ${user.sMSTokenBalance ?? 0}.`,
          );
        }
      }

      if (callbackStatus === "success") {
        setMessage("Confirming payment with the server...");
        try {
          const response = await fetch(
            `${API_URL}/payments/callback?transactionReference=${encodeURIComponent(reference)}`,
            { headers: { Accept: "application/json" } },
          );
          const result = await response.json();
          if (!response.ok) {
            throw new Error(result.message || `Could not verify the payment (HTTP ${response.status}).`);
          }

          const verifiedStatus = String(result?.status ?? "").toLowerCase();
          if (verifiedStatus !== "success") {
            setMessage(`Payment is ${verifiedStatus || "not confirmed"}; your token balance has not been changed.`);
            return;
          }

          if (!authToken) {
            setMessage("Payment verified and credited. Sign in to refresh your displayed balance.");
            return;
          }

          setMessage("Payment verified and credited. Refreshing your token balance...");
          await refreshBalance();
        } catch (refreshError) {
          if (!cancelled) {
            setError(refreshError.message || "Could not verify the payment.");
          }
        }
        return;
      }

      if (!authToken) {
        setError("Sign in to verify this payment and refresh your token balance.");
        return;
      }

      if (callbackStatus === "failed" || callbackStatus === "failure") {
        setMessage("TGIPAY reports that this payment failed.");
        return;
      }

      setMessage(`Verifying payment ${reference} with the server...`);
      const deadline = Date.now() + 5 * 60 * 1000;

      try {
        while (!cancelled && Date.now() < deadline) {
          const response = await fetch(
            `${API_URL}/payments/status/${encodeURIComponent(reference)}`,
            { headers: { Authorization: `Bearer ${authToken}` } },
          );
          const result = await response.json();
          if (!response.ok) {
            throw new Error(result.message || `Could not verify the payment (HTTP ${response.status}).`);
          }

          const status = String(result?.data?.status ?? result?.status ?? "").toLowerCase();
          if (status === "success") {
            await refreshBalance();
            return;
          }

          if (status === "failed" || status === "failure") {
            if (!cancelled) setMessage("TGIPAY reports that this payment failed.");
            return;
          }

          if (!cancelled) {
            setMessage(`Payment is ${status || "pending"}; checking again...`);
          }
          await new Promise((resolve) => {
            timeoutId = window.setTimeout(resolve, 5000);
          });
        }

        if (!cancelled) {
          setMessage("Payment is still pending. Your balance updates after TGIPAY confirms success.");
        }
      } catch (checkError) {
        if (!cancelled) setError(checkError.message || "Could not verify the payment.");
      }
    }

    checkPayment();
    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
    };
  }, []);

  if (!message && !error) return null;

  return (
    <div
      role={error ? "alert" : "status"}
      className={`mb-4 rounded-lg p-3 text-sm ${
        error ? "bg-red-50 text-red-700" : "bg-green-50 text-green-800"
      }`}
    >
      {error || message}
    </div>
  );
}

export default PaymentReturnNotice;
