package com.crochet.store.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.crochet.store.entity.Order;
import com.crochet.store.entity.OrderItem;
import com.crochet.store.entity.Product;
import com.crochet.store.repository.OrderRepository;
import com.crochet.store.repository.ProductRepository;

import jakarta.transaction.Transactional;

@Service
public class OrderExpiryService {

	private final OrderRepository orderRepository;
	private final ProductRepository productRepository;
	private final long paymentExpiryMinutes;
	private final long paymentProofExpiryMinutes;

	public OrderExpiryService(OrderRepository orderRepository, ProductRepository productRepository,
			@Value("${app.orders.payment-expiry-minutes:15}") long paymentExpiryMinutes,
			@Value("${app.orders.payment-proof-expiry-minutes:1440}") long paymentProofExpiryMinutes) {

		this.orderRepository = orderRepository;
		this.productRepository = productRepository;
		this.paymentExpiryMinutes = paymentExpiryMinutes;
		this.paymentProofExpiryMinutes = paymentProofExpiryMinutes;
	}

	public Instant newPaymentExpiry() {
		return Instant.now().plus(paymentExpiryMinutes, ChronoUnit.MINUTES);
	}

	public Instant newPaymentReviewExpiry() {
		return Instant.now().plus(paymentProofExpiryMinutes, ChronoUnit.MINUTES);
	}

	@Scheduled(fixedDelayString = "${app.orders.expiry-check-delay-ms:60000}")
	@Transactional
	public void cancelExpiredPendingOrders() {
		List<Order> expiredPendingOrders = orderRepository
				.findByStatusAndPaymentExpiresAtBefore(Order.OrderStatus.PENDING, Instant.now());

		List<Order> expiredSubmittedOrders = orderRepository
				.findByStatusAndPaymentExpiresAtBefore(Order.OrderStatus.PAYMENT_SUBMITTED, Instant.now());

		for (Order order : expiredPendingOrders) {
			releaseStockAndCancel(order);
		}

		for (Order order : expiredSubmittedOrders) {
			releaseStockAndCancel(order);
		}
	}

	@Transactional
	public Order cancelPendingOrder(Long orderId) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

		if (order.getStatus() == Order.OrderStatus.PENDING
				|| order.getStatus() == Order.OrderStatus.PAYMENT_SUBMITTED) {
			releaseStockAndCancel(order);
		}

		return order;
	}

	@Transactional
	public Order cancelPaidOrder(Long orderId) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

		if (order.getStatus() == Order.OrderStatus.PAID) {
			releaseStockAndCancel(order);
		}

		return order;
	}

	private void releaseStockAndCancel(Order order) {
		for (OrderItem item : order.getItems()) {
			Product product = item.getProduct();
			product.setStock(product.getStock() + item.getQuantity());
			productRepository.save(product);
		}

		order.setStatus(Order.OrderStatus.CANCELLED);
		order.setPaymentExpiresAt(null);
		orderRepository.save(order);
	}
}