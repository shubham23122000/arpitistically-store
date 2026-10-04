import { useEffect, useState, type ChangeEvent } from "react";

const API_BASE = import.meta.env.VITE_API_BASE;
const UPI_ID = "992068902@ptsbi";

type OrderItem = {
  productName: string;
  quantity: number;
};

type Order = {
  id: number;
  status: string;
  totalAmount: number;
  createdAt: string;
  items: OrderItem[];
  paymentReference?: string;
  paymentSubmittedAt?: string;
};

export default function MyOrders({
  token,
  goBack,
}: {
  token: string;
  goBack: () => void;
}) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [transactionIds, setTransactionIds] = useState<Record<number, string>>(
    {},
  );
  const [screenshots, setScreenshots] = useState<Record<number, File | null>>(
    {},
  );
  const [submittingOrderId, setSubmittingOrderId] = useState<number | null>(
    null,
  );
  const [message, setMessage] = useState("");

  async function loadOrders() {
    try {
      setLoading(true);

      const response = await fetch(`${API_BASE}/orders/my`, {
        headers: { Authorization: `Bearer ${token}` },
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.message || "Could not load orders.");
      }

      setOrders(data);
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "Could not load orders.",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadOrders();
  }, [token]);

  function chooseScreenshot(
    orderId: number,
    event: ChangeEvent<HTMLInputElement>,
  ) {
    setScreenshots((current) => ({
      ...current,
      [orderId]: event.target.files?.[0] || null,
    }));
  }

  async function submitPayment(orderId: number) {
    const transactionId = transactionIds[orderId]?.trim();

    if (!transactionId) {
      setMessage("Please enter the UPI transaction ID / UTR first.");
      return;
    }

    const formData = new FormData();
    formData.append("transactionId", transactionId);

    if (screenshots[orderId]) {
      formData.append("screenshot", screenshots[orderId] as File);
    }

    try {
      setSubmittingOrderId(orderId);
      setMessage("");

      const response = await fetch(
        `${API_BASE}/orders/${orderId}/submit-payment`,
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}` },
          body: formData,
        },
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || "Could not submit payment details.");
      }

      setMessage(
        "Payment details submitted. We will verify the payment shortly.",
      );
      await loadOrders();
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "Could not submit payment details.",
      );
    } finally {
      setSubmittingOrderId(null);
    }
  }

  if (loading) return <p className="status-message">Loading orders…</p>;

  return (
    <div className="app-shell">
      <main>
        <section className="store-layout">
          <div>
            <div className="section-heading">
              <div>
                <p className="eyebrow">Your orders</p>
                <h2>Order History</h2>
              </div>

              <button className="text-button" onClick={goBack}>
                ← Back
              </button>
            </div>

            {message && <p className="status-message">{message}</p>}

            {orders.length === 0 ? (
              <p className="empty-products">No orders yet.</p>
            ) : (
              <div className="order-list">
                {orders.map((order) => (
                  <div key={order.id} className="order-card">
                    <div className="order-card-header">
                      <div>
                        <strong>Order #{order.id}</strong>
                        <span>
                          {new Date(order.createdAt).toLocaleDateString(
                            "en-GB",
                            {
                              day: "2-digit",
                              month: "short",
                              year: "numeric",
                            },
                          )}
                        </span>
                      </div>

                      <span className={`status-badge status-${order.status.toLowerCase()}`}>
                        {order.status.replace("_", " ").toLowerCase()}
                      </span>
                    </div>

                    <div className="order-items">
                      {order.items.map((item, index) => (
                        <p key={index}>
                          {item.productName} <b>× {item.quantity}</b>
                        </p>
                      ))}
                    </div>

                    <div className="order-card-footer">
                      <strong>Total: ₹{order.totalAmount}</strong>
                    </div>

                    {order.status === "PENDING" && (
                      <div className="payment-submission">
                        <p>
                          Pay <strong>₹{order.totalAmount}</strong> to{" "}
                          <strong>{UPI_ID}</strong>, then submit the UTR below.
                        </p>

                        <label>
                          UPI transaction ID / UTR
                          <input
                            value={transactionIds[order.id] || ""}
                            onChange={(event) =>
                              setTransactionIds((current) => ({
                                ...current,
                                [order.id]: event.target.value,
                              }))
                            }
                            placeholder="Example: 412345678901"
                          />
                        </label>

                        <label>
                          Payment screenshot (optional)
                          <input
                            type="file"
                            accept="image/*"
                            onChange={(event) =>
                              chooseScreenshot(order.id, event)
                            }
                          />
                        </label>

                        <button
                          className="checkout-button"
                          disabled={submittingOrderId === order.id}
                          onClick={() => void submitPayment(order.id)}
                        >
                          {submittingOrderId === order.id
                            ? "Submitting…"
                            : "Submit payment details"}
                        </button>
                      </div>
                    )}

                    {order.status === "PAYMENT_SUBMITTED" && (
                      <p className="payment-review-message">
                        Payment details received. Awaiting verification.
                      </p>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}