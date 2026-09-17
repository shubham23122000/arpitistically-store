package com.crochet.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderDtos {

	// What the frontend sends at checkout — just product ids + quantities.
	// Prices are never trusted from the client; the backend looks them up itself.
	public static class CheckoutRequest {
		@NotEmpty
		@Valid
		private List<CheckoutItem> items;

		@NotBlank
		private String shippingAddress;

		public List<CheckoutItem> getItems() {
			return items;
		}

		public void setItems(List<CheckoutItem> items) {
			this.items = items;
		}

		public String getShippingAddress() {
			return shippingAddress;
		}

		public void setShippingAddress(String shippingAddress) {
			this.shippingAddress = shippingAddress;
		}
	}

	public static class CheckoutItem {
		@NotNull
		private Long productId;

		@NotNull
		@Positive
		private Integer quantity;

		public Long getProductId() {
			return productId;
		}

		public void setProductId(Long productId) {
			this.productId = productId;
		}

		public Integer getQuantity() {
			return quantity;
		}

		public void setQuantity(Integer quantity) {
			this.quantity = quantity;
		}
	}

	public static class OrderItemResponse {
		private Long productId;
		private String productName;
		private Integer quantity;
		private BigDecimal unitPrice;

		public OrderItemResponse(Long productId, String productName, Integer quantity, BigDecimal unitPrice) {
			this.productId = productId;
			this.productName = productName;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
		}

		public Long getProductId() {
			return productId;
		}

		public String getProductName() {
			return productName;
		}

		public Integer getQuantity() {
			return quantity;
		}

		public BigDecimal getUnitPrice() {
			return unitPrice;
		}
	}

	public static class OrderResponse {
		private Long id;
		private String status;
		private BigDecimal totalAmount;
		private String shippingAddress;
		private Instant createdAt;
		private List<OrderItemResponse> items;

		public OrderResponse(Long id, String status, BigDecimal totalAmount, String shippingAddress, Instant createdAt,
				List<OrderItemResponse> items) {
			this.id = id;
			this.status = status;
			this.totalAmount = totalAmount;
			this.shippingAddress = shippingAddress;
			this.createdAt = createdAt;
			this.items = items;
		}

		public Long getId() {
			return id;
		}

		public String getStatus() {
			return status;
		}

		public BigDecimal getTotalAmount() {
			return totalAmount;
		}

		public String getShippingAddress() {
			return shippingAddress;
		}

		public Instant getCreatedAt() {
			return createdAt;
		}

		public List<OrderItemResponse> getItems() {
			return items;
		}
	}

	public static class CheckoutResponse {
		private final OrderResponse order;
		private final String razorpayOrderId;
		private final String razorpayKeyId;
		private final long amountInPaise;
		private final String currency;

		public CheckoutResponse(OrderResponse order, String razorpayOrderId, String razorpayKeyId, long amountInPaise,
				String currency) {
			this.order = order;
			this.razorpayOrderId = razorpayOrderId;
			this.razorpayKeyId = razorpayKeyId;
			this.amountInPaise = amountInPaise;
			this.currency = currency;
		}

		public OrderResponse getOrder() {
			return order;
		}

		public String getRazorpayOrderId() {
			return razorpayOrderId;
		}

		public String getRazorpayKeyId() {
			return razorpayKeyId;
		}

		public long getAmountInPaise() {
			return amountInPaise;
		}

		public String getCurrency() {
			return currency;
		}
	}

	public static class PaymentVerificationRequest {
		@NotBlank
		private String razorpayPaymentId;

		@NotBlank
		private String razorpayOrderId;

		@NotBlank
		private String razorpaySignature;

		public String getRazorpayPaymentId() {
			return razorpayPaymentId;
		}

		public void setRazorpayPaymentId(String razorpayPaymentId) {
			this.razorpayPaymentId = razorpayPaymentId;
		}

		public String getRazorpayOrderId() {
			return razorpayOrderId;
		}

		public void setRazorpayOrderId(String razorpayOrderId) {
			this.razorpayOrderId = razorpayOrderId;
		}

		public String getRazorpaySignature() {
			return razorpaySignature;
		}

		public void setRazorpaySignature(String razorpaySignature) {
			this.razorpaySignature = razorpaySignature;
		}
	}
}