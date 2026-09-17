package com.crochet.store.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.crochet.store.entity.AppUser;
import com.crochet.store.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
	List<Order> findByUserOrderByCreatedAtDesc(AppUser user);

	List<Order> findAllByOrderByCreatedAtDesc();

	List<Order> findByStatusAndPaymentExpiresAtBefore(Order.OrderStatus status, Instant paymentExpiresAt);
}
