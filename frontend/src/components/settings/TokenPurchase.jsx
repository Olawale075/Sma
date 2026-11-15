import { useState } from "react";
import PaymentReturnNotice from "./PaymentReturnNotice";

const API_URL = import.meta.env.VITE_API_URL || "https://fireeyes-detector-ib01.onrender.com";

function TokenPurchase() {
  const [tokenType, setTokenType] = useState("AI_TOKEN");
  const [quantity, setQuantity] = useState("10");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");

  const quantityNumber = Number(quantity);
  const price = tokenType === "AI_TOKEN"
    ? quantityNumber * 10
    : Number((quantityNumber * 100 / 35).toFixed(2));

  async function startPurchase(event) {
    event.preventDefault();
    setError("");

    const authToken = localStorage.getItem("token");
    if (!authToken) {
      setError("Please sign in before purchasing tokens.");
      return;
    }
    if (!Number.isInteger(quantityNumber) || quantityNumber < 1 || quantityNumber > 100000) {
      setError("Choose a whole number of tokens between 1 and 100,000.");
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await fetch(`${API_URL}/payments/initiate`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${authToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ tokenType, tokenQuantity: quantityNumber }),
      });
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.message || data.error || "Could not start payment.");
      }
      if (!data.paymentUrl) {
        throw new Error("The payment service did not return a checkout URL.");
      }
      window.location.assign(data.paymentUrl);
    } catch (purchaseError) {
      setError(purchaseError.message || "Could not start payment.");
      setIsSubmitting(false);
    }
  }

  return (
    <section className="rounded-2xl bg-white p-6 shadow-sm">
      <h2 className="text-xl font-semibold text-gray-800">Buy tokens</h2>
      <p className="mt-2 text-sm text-gray-500">
        Choose your token type and quantity. The server calculates the NGN price and creates your TGI checkout.
      </p>

      <PaymentReturnNotice />
      {error && <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      <form className="mt-5 space-y-4" onSubmit={startPurchase}>
        <label className="block text-sm font-medium text-gray-700">
          Token type
          <select
            className="mt-1 block w-full rounded-lg border border-gray-300 p-3"
            value={tokenType}
            onChange={(event) => setTokenType(event.target.value)}
          >
            <option value="AI_TOKEN">Aitoken — 10 tokens for ₦100</option>
            <option value="SMS_TOKEN">Smstoken — 35 tokens for ₦100</option>
          </select>
        </label>

        <label className="block text-sm font-medium text-gray-700">
          Quantity
          <input
            className="mt-1 block w-full rounded-lg border border-gray-300 p-3"
            type="number"
            min="1"
            max="100000"
            step="1"
            required
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
          />
        </label>

        <p className="text-sm font-medium text-gray-700">
          Estimated price: ₦{Number.isFinite(price) ? price.toFixed(2) : "0.00"}
        </p>
        <button
          className="w-full rounded-lg bg-green-700 px-4 py-3 font-semibold text-white hover:bg-green-800 disabled:cursor-not-allowed disabled:opacity-60"
          type="submit"
          disabled={isSubmitting}
        >
          {isSubmitting ? "Starting secure checkout..." : "Continue to payment"}
        </button>
      </form>
    </section>
  );
}

export default TokenPurchase;
