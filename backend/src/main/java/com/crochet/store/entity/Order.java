package com.crochet.store.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "user_id")
	private AppUser user;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status = OrderStatus.PENDING;

	// Manual UPI payment details. Never treat these as proof of payment
	// automatically.
	private String paymentReference;
	private String paymentProofPublicId;
	private Instant paymentSubmittedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	private Instant paymentExpiresAt;
	private String shippingAddress;

	public enum OrderStatus {
		PENDING, PAYMENT_SUBMITTED, PAID, SHIPPED, DELIVERED, CANCELLED
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public AppUser getUser() {
		return user;
	}

	public void setUser(AppUser user) {
		this.user = user;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public void setItems(List<OrderItem> items) {
		this.items = items;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	public String getPaymentReference() {
		return paymentReference;
	}

	public void setPaymentReference(String paymentReference) {
		this.paymentReference = paymentReference;
	}

	public String getPaymentProofPublicId() {
		return paymentProofPublicId;
	}

	public void setPaymentProofPublicId(String paymentProofPublicId) {
		this.paymentProofPublicId = paymentProofPublicId;
	}

	public Instant getPaymentSubmittedAt() {
		return paymentSubmittedAt;
	}

	public void setPaymentSubmittedAt(Instant paymentSubmittedAt) {
		this.paymentSubmittedAt = paymentSubmittedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public String getShippingAddress() {
		return shippingAddress;
	}

	public void setShippingAddress(String shippingAddress) {
		this.shippingAddress = shippingAddress;
	}

	public Instant getPaymentExpiresAt() {
		return paymentExpiresAt;
	}

	public void setPaymentExpiresAt(Instant paymentExpiresAt) {
		this.paymentExpiresAt = paymentExpiresAt;
	}
}
