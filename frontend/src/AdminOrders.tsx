import { useEffect, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE;

type OrderItem = {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
};

type StoreOrder = {
  id: number;
  status: "PENDING" | "PAID" | "SHIPPED" | "DELIVERED" | "CANCELLED";
  totalAmount: number;
  shippingAddress: string;
  createdAt: string;
  items: OrderItem[];
};

type AdminOrdersProps = {
  token: string;
};

const formatPrice = (price: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
  }).format(price);

function formatDate(date: string) {
  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(date));
}

function availableStatuses(status: StoreOrder["status"]) {
  if (status === "PAID") return ["PAID", "SHIPPED", "DELIVERED"];
  if (status === "SHIPPED") return ["SHIPPED", "DELIVERED"];
  return [status];
}

export default function AdminOrders({ token }: AdminOrdersProps) {
  const [orders, setOrders] = useState<StoreOrder[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [updatingOrderId, setUpdatingOrderId] = useState<number | null>(null);

  useEffect(() => {
    void loadOrders();
  }, []);

  async function loadOrders() {
    try {
      setIsLoading(true);
      setError("");

      const response = await fetch(`${API_BASE_URL}/orders/admin`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || "Could not load orders.");
      }

      setOrders(data as StoreOrder[]);
    } catch (loadError) {
      setError(
        loadError instanceof Error ? loadError.message : "Could not load orders.",
      );
    } finally {
      setIsLoading(false);
    }
  }

  async function updateStatus(orderId: number, status: string) {
    setError("");
    setUpdatingOrderId(orderId);

    try {
      const response = await fetch(
        `${API_BASE_URL}/orders/admin/${orderId}/status`,
        {
          method: "PATCH",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({ status }),
        },
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || "Could not update order status.");
      }

      setOrders((currentOrders) =>
        currentOrders.map((order) =>
          order.id === orderId ? (data as StoreOrder) : order,
        ),
      );
    } catch (updateError) {
      setError(
        updateError instanceof Error
          ? updateError.message
          : "Could not update order status.",
      );
    } finally {
      setUpdatingOrderId(null);
    }
  }

  return (
    <section className="admin-orders">
      <div className="admin-section-title">
        <div>
          <p className="eyebrow">Fulfilment</p>
          <h3>Customer orders</h3>
        </div>

        <button className="text-button" onClick={() => void loadOrders()}>
          Refresh orders
        </button>
      </div>

      {isLoading && <p className="admin-note">Loading orders…</p>}
      {error && <p className="auth-error">{error}</p>}

      {!isLoading && !error && orders?.length === 0 && (
        <p className="admin-note">No orders have been placed yet.</p>
      )}

      <div className="order-list">
        {orders?.map((order) => (
          <article className="order-card" key={order.id}>
            <div className="order-card-header">
              <div>
                <strong>Order #{order.id}</strong>
                <span>{formatDate(order.createdAt)}</span>
              </div>

              <span className={`status-badge status-${order.status.toLowerCase()}`}>
                {order.status}
              </span>
            </div>

            <div className="order-items">
              {order.items.map((item) => (
                <p key={`${order.id}-${item.productId}`}>
                  {item.quantity} × {item.productName} —{" "}
                  {formatPrice(item.unitPrice * item.quantity)}
                </p>
              ))}
            </div>

            <p className="order-address">
              <strong>Deliver to:</strong> {order.shippingAddress}
            </p>

            <div className="order-card-footer">
              <strong>{formatPrice(order.totalAmount)}</strong>

              <select
                value={order.status}
                disabled={
                  updatingOrderId === order.id ||
                  !["PAID", "SHIPPED"].includes(order.status)
                }
                onChange={(event) =>
                  void updateStatus(order.id, event.target.value)
                }
              >
                {availableStatuses(order.status).map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}