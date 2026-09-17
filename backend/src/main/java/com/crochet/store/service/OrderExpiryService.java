package com.crochet.store.service;

import com.crochet.store.entity.Order;
import com.crochet.store.entity.OrderItem;
import com.crochet.store.entity.Product;
import com.crochet.store.repository.OrderRepository;
import com.crochet.store.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OrderExpiryService {

	private final OrderRepository orderRepository;
	private final ProductRepository productRepository;
	private final long paymentExpiryMinutes;

	public OrderExpiryService(OrderRepository orderRepository, ProductRepository productRepository,
			@Value("${app.orders.payment-expiry-minutes:15}") long paymentExpiryMinutes) {
		this.orderRepository = orderRepository;
		this.productRepository = productRepository;
		this.paymentExpiryMinutes = paymentExpiryMinutes;
	}

	public Instant newPaymentExpiry() {
		return Instant.now().plus(paymentExpiryMinutes, ChronoUnit.MINUTES);
	}

	@Scheduled(fixedDelayString = "${app.orders.expiry-check-delay-ms:60000}")
	@Transactional
	public void cancelExpiredPendingOrders() {
		List<Order> expiredOrders = orderRepository.findByStatusAndPaymentExpiresAtBefore(Order.OrderStatus.PENDING,
				Instant.now());

		for (Order order : expiredOrders) {
			releaseStockAndCancel(order);
		}
	}

	@Transactional
	public Order cancelPendingOrder(Long orderId) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

		if (order.getStatus() == Order.OrderStatus.PENDING) {
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