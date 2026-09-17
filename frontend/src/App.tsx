import { useEffect, useMemo, useState, type FormEvent } from "react";
import AdminPanel from "./AdminPanel";
import MyOrders from "./MyOrders";
import ProductCard from "./ProductCard";
import ProductDetail from "./ProductDetail";

const API_BASE_URL = import.meta.env.VITE_API_BASE;
const SESSION_STORAGE_KEY = "crochet-store-session";
console.log(API_BASE_URL);

type Product = {
  id: number;
  name: string;
  description: string | null;
  price: number;
  stock: number;
  category: string | null;
  imageUrls: string[];
};

type CartItem = {
  product: Product;
  quantity: number;
};

type Session = {
  token: string;
  email: string;
  fullName: string;
  role: string;
};

type AuthMode = "login" | "register";

type CheckoutResponse = {
  order: {
    id: number;
  };
  razorpayOrderId: string;
  razorpayKeyId: string;
  amountInPaise: number;
  currency: string;
};

type RazorpaySuccessResponse = {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
};

type RazorpayOptions = {
  key: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  order_id: string;
  prefill: {
    name: string;
    email: string;
  };
  theme: {
    color: string;
  };
  handler: (response: RazorpaySuccessResponse) => void;
};

type RazorpayInstance = {
  open: () => void;
  on: (event: string, callback: () => void) => void;
};

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => RazorpayInstance;
  }
}

const formatPrice = (price: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
  }).format(price);

function getSavedSession(): Session | null {
  try {
    const saved = localStorage.getItem(SESSION_STORAGE_KEY);
    return saved ? (JSON.parse(saved) as Session) : null;
  } catch {
    return null;
  }
}

function loadRazorpayScript(): Promise<boolean> {
  return new Promise((resolve) => {
    if (window.Razorpay) {
      resolve(true);
      return;
    }

    const existingScript = document.getElementById("razorpay-checkout-script");

    if (existingScript) {
      existingScript.addEventListener("load", () => resolve(true));
      existingScript.addEventListener("error", () => resolve(false));
      return;
    }

    const script = document.createElement("script");
    script.id = "razorpay-checkout-script";
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);

    document.body.appendChild(script);
  });
}

function App() {
  const [products, setProducts] = useState<Product[]>([]);
  const [cart, setCart] = useState<CartItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const [session, setSession] = useState<Session | null>(getSavedSession);
  const [isAuthOpen, setIsAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState<AuthMode>("login");
  const [authError, setAuthError] = useState("");
  const [isSubmittingAuth, setIsSubmittingAuth] = useState(false);

  const [shippingAddress, setShippingAddress] = useState("");
  const [checkoutError, setCheckoutError] = useState("");
  const [isCheckingOut, setIsCheckingOut] = useState(false);
  const [isAdminOpen, setIsAdminOpen] = useState(false);
  const [view, setView] = useState("home");
  const [selectedProduct, setSelectedProduct] = useState(null);

  useEffect(() => {
    void loadProducts();
  }, []);

  async function loadProducts() {
    try {
      setIsLoading(true);
      setError("");

      const response = await fetch(`${API_BASE_URL}/products`);

      if (!response.ok) {
        throw new Error("Could not load products.");
      }

      const data: Product[] = await response.json();
      setProducts(data);
    } catch {
      setError(
        "Could not connect to the store. Make sure the Spring Boot backend is running on port 8080.",
      );
    } finally {
      setIsLoading(false);
    }
  }

  function addToCart(product: Product) {
    if (product.stock === 0) return;

    setCart((currentCart) => {
      const existingItem = currentCart.find(
        (item) => item.product.id === product.id,
      );

      if (!existingItem) {
        return [...currentCart, { product, quantity: 1 }];
      }

      if (existingItem.quantity >= product.stock) {
        return currentCart;
      }

      return currentCart.map((item) =>
        item.product.id === product.id
          ? { ...item, quantity: item.quantity + 1 }
          : item,
      );
    });
  }

  function changeQuantity(productId: number, change: number) {
    setCart((currentCart) =>
      currentCart
        .map((item) => {
          if (item.product.id !== productId) return item;

          const quantity = Math.max(
            0,
            Math.min(item.quantity + change, item.product.stock),
          );

          return { ...item, quantity };
        })
        .filter((item) => item.quantity > 0),
    );
  }

  function openAuth(mode: AuthMode) {
    setAuthMode(mode);
    setAuthError("");
    setIsAuthOpen(true);
  }

  function logout() {
    localStorage.removeItem(SESSION_STORAGE_KEY);
    setSession(null);
  }

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthError("");
    setIsSubmittingAuth(true);

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email") || "");
    const password = String(formData.get("password") || "");
    const fullName = String(formData.get("fullName") || "");

    const body =
      authMode === "register"
        ? { fullName, email, password }
        : { email, password };

    try {
      const response = await fetch(`${API_BASE_URL}/auth/${authMode}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || "Authentication failed.");
      }

      const newSession = data as Session;
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(newSession));
      setSession(newSession);
      setIsAuthOpen(false);
    } catch (authException) {
      setAuthError(
        authException instanceof Error
          ? authException.message
          : "Something went wrong. Please try again.",
      );
    } finally {
      setIsSubmittingAuth(false);
    }
  }

  async function verifyPayment(
    checkout: CheckoutResponse,
    payment: RazorpaySuccessResponse,
  ) {
    if (!session) return;

    try {
      const response = await fetch(
        `${API_BASE_URL}/orders/${checkout.order.id}/verify-payment`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${session.token}`,
          },
          body: JSON.stringify({
            razorpayPaymentId: payment.razorpay_payment_id,
            razorpayOrderId: payment.razorpay_order_id,
            razorpaySignature: payment.razorpay_signature,
          }),
        },
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || "Could not verify the payment.");
      }

      setCart([]);
      setShippingAddress("");
      setCheckoutError("");
      await loadProducts();

      alert("Payment successful! Your order has been placed.");
    } catch (verificationError) {
      setCheckoutError(
        verificationError instanceof Error
          ? verificationError.message
          : "Payment completed, but verification failed. Please contact support.",
      );
    }
  }

  async function startCheckout() {
    if (!session) {
      openAuth("login");
      return;
    }

    if (cart.length === 0) {
      setCheckoutError("Your cart is empty.");
      return;
    }

    if (!shippingAddress.trim()) {
      setCheckoutError("Please enter a shipping address.");
      return;
    }

    setCheckoutError("");
    setIsCheckingOut(true);

    try {
      const response = await fetch(`${API_BASE_URL}/orders/checkout`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.token}`,
        },
        body: JSON.stringify({
          items: cart.map((item) => ({
            productId: item.product.id,
            quantity: item.quantity,
          })),
          shippingAddress: shippingAddress.trim(),
        }),
      });

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || "Could not create your order.");
      }

      const checkout = data as CheckoutResponse;
      const scriptLoaded = await loadRazorpayScript();

      if (!scriptLoaded || !window.Razorpay) {
        throw new Error(
          "Could not load Razorpay Checkout. Please check your internet connection.",
        );
      }

      const Razorpay = window.Razorpay;

      const razorpay = new Razorpay({
        key: checkout.razorpayKeyId,
        amount: checkout.amountInPaise,
        currency: checkout.currency,
        name: "Arpitistically",
        description: "Handmade crochet order",
        order_id: checkout.razorpayOrderId,
        prefill: {
          name: session.fullName,
          email: session.email,
        },
        theme: {
          color: "#58354d",
        },
        handler: (payment) => {
          void verifyPayment(checkout, payment);
        },
      });

      razorpay.on("payment.failed", () => {
        setCheckoutError(
          "Payment failed or was cancelled. Please try again with another method.",
        );
      });

      razorpay.open();
    } catch (checkoutException) {
      setCheckoutError(
        checkoutException instanceof Error
          ? checkoutException.message
          : "Could not start checkout.",
      );
    } finally {
      setIsCheckingOut(false);
    }
  }

  const cartTotal = useMemo(
    () =>
      cart.reduce(
        (total, item) => total + item.product.price * item.quantity,
        0,
      ),
    [cart],
  );

  const cartCount = useMemo(
    () => cart.reduce((total, item) => total + item.quantity, 0),
    [cart],
  );

  if (view === "orders") {
    return (
      <MyOrders
        token={session?.token || ""}
        goBack={() => setView("home")}
      />
    );
  }

  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand" href="/">
          <span className="brand-icon">🧶</span>
          <span className="brand-name">Arpitistically</span>
        </a>

        <div className="header-actions">
          {session ? (
            <>
              <span className="welcome-text">Hi, {session.fullName}</span>
              <button className="account-button" onClick={logout}>
                Log out
              </button>

              {session?.role === "ADMIN" && (
                <button
                  className="account-button filled"
                  onClick={() => setIsAdminOpen(true)}
                >
                  Admin
                </button>
              )}

              {session && (
                <button
                  className="account-button"
                  onClick={() => setView("orders")}
                >
                  My Orders
                </button>
              )}
            </>
          ) : (
            <>
              <button className="account-button" onClick={() => openAuth("login")}>
                Log in
              </button>
              <button
                className="account-button filled"
                onClick={() => openAuth("register")}
              >
                Sign up
              </button>
            </>
          )}

          <div className="cart-indicator">
            Cart <span>{cartCount}</span>
          </div>
        </div>
      </header>

      <main>
        <section className="hero">
          <h1>Handcrafted Crochet Creations</h1>
          <p className="tagline">
            Made with love, designed to bring warmth and charm to your everyday style.
          </p>
          <p className="insta-cta">
            Follow us on Instagram 💖{" "}
            <a
              href="https://www.instagram.com/arpitistically"
              target="_blank"
              rel="noopener noreferrer"
            >
              @arpitistically
            </a>
          </p>
        </section>

        <section className="store-layout">
          <div>
            <div className="section-heading">
              <div>
                <h2>Our Handmade Collection</h2>
                <p>
                  Thoughtfully crafted crochet pieces designed to add warmth and charm.
                </p>
              </div>

              <button className="text-button" onClick={() => void loadProducts()}>
                Refresh
              </button>
            </div>

            {isLoading && <p className="status-message">Loading products…</p>}
            {error && <p className="error-message">{error}</p>}

            <div className="product-grid">
              {products.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  addToCart={addToCart}
                  onClick={() => setSelectedProduct(product)}
                />
              ))}
            </div>
          </div>

          <aside className="cart-panel">
            <div className="cart-title">
              <div>
                <p className="eyebrow">Your selection</p>
                <h2>Cart</h2>
              </div>
              <span>{cartCount} items</span>
            </div>

            {cart.length === 0 ? (
              <p className="empty-cart">
                Your cart is waiting for something lovely.
              </p>
            ) : (
              <>
                <div className="cart-items">
                  {cart.map((item) => (
                    <div className="cart-item" key={item.product.id}>
                      <div>
                        <h3>{item.product.name}</h3>
                        <p>{formatPrice(item.product.price)}</p>
                      </div>

                      <div className="quantity-controls">
                        <button onClick={() => changeQuantity(item.product.id, -1)}>
                          −
                        </button>
                        <span>{item.quantity}</span>
                        <button onClick={() => changeQuantity(item.product.id, 1)}>
                          +
                        </button>
                      </div>
                    </div>
                  ))}
                </div>

                <label className="shipping-field">
                  Shipping address
                  <textarea
                    value={shippingAddress}
                    onChange={(event) => setShippingAddress(event.target.value)}
                    placeholder="House / street, city, state, PIN code"
                    rows={3}
                  />
                </label>

                {checkoutError && (
                  <p className="checkout-error">{checkoutError}</p>
                )}

                <div className="cart-total">
                  <span>Total</span>
                  <strong>{formatPrice(cartTotal)}</strong>
                </div>

                <button
                  className="checkout-button"
                  disabled={isCheckingOut}
                  onClick={() => void startCheckout()}
                >
                  {isCheckingOut ? "Opening payment…" : "Pay securely"}
                </button>
              </>
            )}
          </aside>
        </section>
      </main>
      <div className="custom-order-box">
        <p>
          Want something custom? 💌
          DM us on Instagram to create your own crochet design!
        </p>
      </div>
      <footer>Made by hand, packed with love.</footer>
      {isAdminOpen && session?.role === "ADMIN" && (
        <AdminPanel
          token={session.token}
          products={products}
          onProductsChanged={loadProducts}
          onClose={() => setIsAdminOpen(false)}
        />
      )}
      {isAuthOpen && (
        <div className="modal-backdrop" onMouseDown={() => setIsAuthOpen(false)}>
          <section
            className="auth-modal"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button
              className="modal-close"
              aria-label="Close"
              onClick={() => setIsAuthOpen(false)}
            >
              ×
            </button>

            <p className="eyebrow">
              {authMode === "login" ? "Welcome back" : "Join the store"}
            </p>
            <h2>{authMode === "login" ? "Log in" : "Create your account"}</h2>

            <form onSubmit={submitAuth}>
              {authMode === "register" && (
                <label>
                  Full name
                  <input name="fullName" required placeholder="Your name" />
                </label>
              )}

              <label>
                Email address
                <input
                  name="email"
                  type="email"
                  required
                  placeholder="you@example.com"
                />
              </label>

              <label>
                Password
                <input
                  name="password"
                  type="password"
                  minLength={8}
                  required
                  placeholder="At least 8 characters"
                />
              </label>

              {authError && <p className="auth-error">{authError}</p>}

              <button
                className="checkout-button"
                disabled={isSubmittingAuth}
                type="submit"
              >
                {isSubmittingAuth
                  ? "Please wait…"
                  : authMode === "login"
                    ? "Log in"
                    : "Create account"}
              </button>
            </form>

            <p className="auth-switch">
              {authMode === "login"
                ? "New here?"
                : "Already have an account?"}{" "}
              <button
                onClick={() =>
                  setAuthMode(authMode === "login" ? "register" : "login")
                }
              >
                {authMode === "login" ? "Create an account" : "Log in"}
              </button>
            </p>
          </section>
        </div>
      )}
      {selectedProduct && (
        <div
          className="modal-overlay"
          onClick={() => setSelectedProduct(null)}
        >
          <div
            className="modal-content"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              className="close-btn"
              onClick={() => setSelectedProduct(null)}
            >
              ✕
            </button>

            <ProductDetail
              product={selectedProduct}
              addToCart={addToCart}
            />
          </div>
        </div>
      )}
    </div>
  );
}



export default App;