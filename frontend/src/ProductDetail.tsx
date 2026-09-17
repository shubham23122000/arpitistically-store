import { useState } from "react";

export default function ProductDetail({ product, addToCart }: any) {
    const [selectedImage, setSelectedImage] = useState(
        product.imageUrls?.[0]
    );

    return (
        <div className="product-detail">

            <div className="detail-images">
                <img src={selectedImage} className="detail-main-img" />

                <div className="thumbnail-row">
                    {product.imageUrls?.map((img: string, i: number) => (
                        <img
                            key={i}
                            src={img}
                            className={`thumbnail ${selectedImage === img ? "active-thumb" : ""
                                }`}
                            onClick={() => setSelectedImage(img)}
                        />
                    ))}
                </div>
            </div>

            <div className="detail-info">
                <h2>{product.name}</h2>
                <p>{product.description}</p>

                <h3>₹{product.price}</h3>

                <button
                    className="primary-button"
                    disabled={product.stock === 0}
                    onClick={() => addToCart(product)}
                >
                    {product.stock === 0 ? "Sold out" : "Add to cart"}
                </button>
            </div>

        </div>
    );
}