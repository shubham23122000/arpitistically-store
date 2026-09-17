import { useState, type FormEvent } from "react";
import AdminOrders from "./AdminOrders";

const API_BASE_URL = import.meta.env.VITE_API_BASE;

type Product = {
    id: number;
    name: string;
    description: string | null;
    price: number;
    stock: number;
    category: string | null;
    imageUrls: string[];
};

type AdminPanelProps = {
    token: string;
    products: Product[];
    onProductsChanged: () => Promise<void>;
    onClose: () => void;
};

type ProductForm = {
    name: string;
    description: string;
    price: string;
    stock: string;
    category: string;
    imageUrls: string[];
};

const emptyForm = (): ProductForm => ({
    name: "",
    description: "",
    price: "",
    stock: "",
    category: "",
    imageUrls: [],
});

export default function AdminPanel({
    token,
    products,
    onProductsChanged,
    onClose,
}: AdminPanelProps) {
    const [form, setForm] = useState<ProductForm>(emptyForm);
    const [editingProductId, setEditingProductId] = useState<number | null>(null);
    const [isUploading, setIsUploading] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");

    function startNewProduct() {
        setForm(emptyForm());
        setEditingProductId(null);
        setError("");
        setMessage("");
    }

    function editProduct(product: Product) {
        setEditingProductId(product.id);
        setForm({
            name: product.name,
            description: product.description || "",
            price: String(product.price),
            stock: String(product.stock),
            category: product.category || "",
            imageUrls: product.imageUrls || [],
        });
        setError("");
        setMessage("");
    }

    async function uploadImages(files: FileList | null) {
        if (!files || files.length === 0) return;

        setError("");
        setMessage("");
        setIsUploading(true);

        try {
            const uploadedUrls: string[] = [];

            for (let i = 0; i < files.length; i++) {
                const body = new FormData();
                body.append("file", files[i]);

                const response = await fetch(
                    `${API_BASE_URL}/products/admin/upload-image`,
                    {
                        method: "POST",
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                        body,
                    }
                );

                const data = await response.json().catch(() => null);

                if (!response.ok) {
                    throw new Error(data?.message || "Could not upload image.");
                }

                uploadedUrls.push(data.url);
            }

            // Add all uploaded images to form
            setForm((current) => ({
                ...current,
                imageUrls: [...current.imageUrls, ...uploadedUrls],
            }));

        } catch (uploadError) {
            setError(
                uploadError instanceof Error
                    ? uploadError.message
                    : "Could not upload images."
            );
        } finally {
            setIsUploading(false);
        }
    }

    function removeImage(url: string) {
        setForm((current) => ({
            ...current,
            imageUrls: current.imageUrls.filter((imageUrl) => imageUrl !== url),
        }));
    }

    async function saveProduct(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        setMessage("");
        setIsSaving(true);

        const requestBody = {
            name: form.name,
            description: form.description,
            price: Number(form.price),
            stock: Number(form.stock),
            category: form.category,
            imageUrls: form.imageUrls,
        };

        try {
            const isEditing = editingProductId !== null;

            const response = await fetch(
                isEditing
                    ? `${API_BASE_URL}/products/admin/${editingProductId}`
                    : `${API_BASE_URL}/products/admin`,
                {
                    method: isEditing ? "PUT" : "POST",
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${token}`,
                    },
                    body: JSON.stringify(requestBody),
                },
            );

            const data = await response.json().catch(() => null);

            if (!response.ok) {
                throw new Error(data?.message || "Could not save product.");
            }

            await onProductsChanged();
            setMessage(isEditing ? "Product updated." : "Product created.");
            startNewProduct();
        } catch (saveError) {
            setError(
                saveError instanceof Error
                    ? saveError.message
                    : "Could not save product.",
            );
        } finally {
            setIsSaving(false);
        }
    }

    async function deleteProduct(product: Product) {
        if (!window.confirm(`Remove "${product.name}" from the store?`)) {
            return;
        }

        setError("");
        setMessage("");

        try {
            const response = await fetch(
                `${API_BASE_URL}/products/admin/${product.id}`,
                {
                    method: "DELETE",
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                },
            );

            if (!response.ok) {
                throw new Error("Could not remove product.");
            }

            if (editingProductId === product.id) {
                startNewProduct();
            }

            await onProductsChanged();
            setMessage("Product removed from the store.");
        } catch (deleteError) {
            setError(
                deleteError instanceof Error
                    ? deleteError.message
                    : "Could not remove product.",
            );
        }
    }

    return (
        <div className="admin-backdrop">
            <section className="admin-panel">
                <div className="admin-header">
                    <div>
                        <p className="eyebrow">Store management</p>
                        <h2>Admin dashboard</h2>
                    </div>

                    <button className="modal-close" onClick={onClose} aria-label="Close">
                        ×
                    </button>
                </div>

                <div className="admin-layout">
                    <div className="admin-products">
                        <div className="admin-section-title">
                            <h3>Products</h3>
                            <button className="text-button" onClick={startNewProduct}>
                                + New product
                            </button>
                        </div>

                        <div className="admin-product-list">
                            {products.map((product) => (
                                <article className="admin-product-row" key={product.id}>
                                    <div className="admin-thumbnail">
                                        {product.imageUrls[0] ? (
                                            <img src={product.imageUrls[0]} alt={product.name} />
                                        ) : (
                                            "🧶"
                                        )}
                                    </div>

                                    <div className="admin-product-info">
                                        <strong>{product.name}</strong>
                                        <span>
                                            ₹{product.price} · {product.stock} in stock
                                        </span>
                                    </div>

                                    <div className="admin-row-actions">
                                        <button onClick={() => editProduct(product)}>Edit</button>
                                        <button
                                            className="danger-button"
                                            onClick={() => void deleteProduct(product)}
                                        >
                                            Remove
                                        </button>
                                    </div>
                                </article>
                            ))}
                        </div>
                    </div>

                    <form className="admin-form" onSubmit={saveProduct}>
                        <div className="admin-section-title">
                            <h3>{editingProductId ? "Edit product" : "New product"}</h3>
                            {editingProductId && (
                                <button className="text-button" type="button" onClick={startNewProduct}>
                                    Cancel edit
                                </button>
                            )}
                        </div>

                        <label>
                            Product name
                            <input
                                required
                                value={form.name}
                                onChange={(event) =>
                                    setForm({ ...form, name: event.target.value })
                                }
                                placeholder="For example, Sunflower keychain"
                            />
                        </label>

                        <label>
                            Description
                            <textarea
                                value={form.description}
                                onChange={(event) =>
                                    setForm({ ...form, description: event.target.value })
                                }
                                placeholder="Describe the product..."
                                rows={3}
                            />
                        </label>

                        <div className="admin-form-row">
                            <label>
                                Price in ₹
                                <input
                                    required
                                    min="1"
                                    step="0.01"
                                    type="number"
                                    value={form.price}
                                    onChange={(event) =>
                                        setForm({ ...form, price: event.target.value })
                                    }
                                />
                            </label>

                            <label>
                                Stock
                                <input
                                    required
                                    min="0"
                                    type="number"
                                    value={form.stock}
                                    onChange={(event) =>
                                        setForm({ ...form, stock: event.target.value })
                                    }
                                />
                            </label>
                        </div>

                        <label>
                            Category
                            <input
                                value={form.category}
                                onChange={(event) =>
                                    setForm({ ...form, category: event.target.value })
                                }
                                placeholder="For example, Flowers"
                            />
                        </label>

                        <label>
                            Product photos
                            <input
                                accept="image/*"
                                disabled={isUploading}
                                type="file"
                                onChange={(event) => void uploadImages(event.target.files)}
                                multiple
                            />
                        </label>

                        {isUploading && <p className="admin-note">Uploading image…</p>}

                        <div className="image-preview-list">
                            {form.imageUrls.map((url) => (
                                <div className="image-preview" key={url}>
                                    <img src={url} alt="Product preview" />
                                    <button
                                        type="button"
                                        onClick={() => removeImage(url)}
                                        aria-label="Remove image"
                                    >
                                        ×
                                    </button>
                                </div>
                            ))}
                        </div>

                        {error && <p className="auth-error">{error}</p>}
                        {message && <p className="admin-success">{message}</p>}

                        <button className="checkout-button" disabled={isSaving} type="submit">
                            {isSaving
                                ? "Saving…"
                                : editingProductId
                                    ? "Save changes"
                                    : "Create product"}
                        </button>
                    </form>
                </div>

                <AdminOrders token={token} />
            </section>
        </div>
    );
}