import { useState } from "react";

export default function ProductCard({ product, addToCart, onClick }: any) {
    const [selectedImage, setSelectedImage] = useState(
        product.imageUrls?.[0]
    );

    return (
        <article className="product-card" onClick={() => onClick(product)}>

            <div className="product-image">
                {selectedImage ? (
                    <img src={selectedImage} alt={product.name} />
                ) : (
                    <span>🧶</span>
                )}
            </div>

            {product.imageUrls?.length > 1 && (
                <div className="thumbnail-row">
                    {product.imageUrls.map((img: string, i: number) => (
                        <img
                            key={i}
                            src={img}
                            className={`thumbnail ${selectedImage === img ? "active-thumb" : ""
                                }`}
                            onClick={() => setSelectedImage(img)}
                        />
                    ))}
                </div>
            )}

            <div className="product-details">
                {product.category && (
                    <p className="product-category">{product.category}</p>
                )}

                <h3>{product.name}</h3>

                <p className="product-description">
                    {product.description || "Handmade with love."}
                </p>

                <div className="product-footer">
                    <strong>₹{product.price}</strong>
                    <span>
                        {product.stock > 0
                            ? `${product.stock} available`
                            : "Sold out"}
                    </span>
                </div>

                <button
                    className="primary-button"
                    disabled={product.stock === 0}
                    onClick={(e) => {
                        e.stopPropagation();
                        addToCart(product);
                    }}
                >
                    {product.stock === 0 ? "Sold out" : "Add to cart"}
                </button>
            </div>
        </article>
    );
}