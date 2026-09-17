import { useEffect, useState } from "react";

const API_BASE = import.meta.env.VITE_API_BASE;

export default function MyOrders({ token, goBack }: any) {
  const [orders, setOrders] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(`${API_BASE}/orders/my`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    })
      .then(async res => {
        const data = await res.json();
        if (!res.ok) {
          throw new Error(data.message || "Something went wrong");
        }
        return data;
      })
      .then(data => {
        console.log("orders:", data);
        setOrders(data);
        setLoading(false);
      })
      .catch(err => {
        console.error(err);
        setLoading(false);
      });
  }, [token]);

  if (loading) return <p>Loading orders...</p>;

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

            {orders.length === 0 ? (
              <p className="empty-products">No orders yet</p>
            ) : (
              <div className="order-list">
                {orders.map((order) => (
                  <div key={order.id} className="order-card">

                    <div className="order-card-header">
                      <div>
                        <strong>Order #{order.id}</strong>
                        <span>
                          {new Date(order.createdAt).toLocaleDateString("en-GB", {
                            day: "2-digit",
                            month: "short",
                            year: "numeric",
                          })}
                          {" "}
                          {new Date(order.createdAt).toLocaleTimeString("en-IN", {
                            hour: "2-digit",
                            minute: "2-digit",
                          })}
                        </span>
                      </div>

                      <span
                        className={`status-badge ${order.status === "PAID"
                          ? "status-paid"
                          : order.status === "PENDING"
                            ? "status-pending"
                            : order.status === "FAILED"
                              ? "status-cancelled"
                              : ""
                          }`}
                      >
                        {order.status.toLowerCase().replace("_", " ")}
                      </span>
                    </div>

                    <div className="order-items">
                      {order.items?.map((item: any, i: number) => (
                        <p key={i}>
                          {item.productName} <b>× {item.quantity}</b>
                        </p>
                      ))}
                    </div>

                    <div className="order-card-footer">
                      <strong>Total: ₹{order.totalAmount}</strong>
                    </div>
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