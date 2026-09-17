package com.crochet.store.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.crochet.store.dto.OrderDtos.CheckoutItem;
import com.crochet.store.dto.OrderDtos.CheckoutRequest;
import com.crochet.store.dto.OrderDtos.CheckoutResponse;
import com.crochet.store.dto.OrderDtos.OrderItemResponse;
import com.crochet.store.dto.OrderDtos.OrderResponse;
import com.crochet.store.dto.OrderDtos.PaymentVerificationRequest;
import com.crochet.store.dto.OrderStatusUpdateRequest;
import com.crochet.store.entity.AppUser;
import com.crochet.store.entity.Order;
import com.crochet.store.entity.OrderItem;
import com.crochet.store.entity.Product;
import com.crochet.store.repository.AppUserRepository;
import com.crochet.store.repository.OrderRepository;
import com.crochet.store.repository.ProductRepository;
import com.crochet.store.service.OrderExpiryService;
import com.crochet.store.service.RazorpayService;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderRepository orderRepository;
	private final ProductRepository productRepository;
	private final AppUserRepository appUserRepository;
	private final RazorpayService razorpayService;
	private final OrderExpiryService orderExpiryService;

	public OrderController(OrderRepository orderRepository, ProductRepository productRepository,
			AppUserRepository appUserRepository, RazorpayService razorpayService,
			OrderExpiryService orderExpiryService) {
		this.orderRepository = orderRepository;
		this.productRepository = productRepository;
		this.appUserRepository = appUserRepository;
		this.razorpayService = razorpayService;
		this.orderExpiryService = orderExpiryService;
	}

	@PostMapping("/checkout")
	@Transactional
	public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request, Authentication auth) {

		AppUser user = currentUser(auth);

		Order order = new Order();
		order.setUser(user);
		order.setShippingAddress(request.getShippingAddress());
		order.setPaymentExpiresAt(orderExpiryService.newPaymentExpiry());

		BigDecimal total = BigDecimal.ZERO;

		for (CheckoutItem itemRequest : request.getItems()) {
			Product product = productRepository.findById(itemRequest.getProductId()).filter(Product::isActive)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
							"Product " + itemRequest.getProductId() + " not found"));

			if (product.getStock() < itemRequest.getQuantity()) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"Not enough stock for \"" + product.getName() + "\" — only " + product.getStock() + " left");
			}

			// Reserve stock until the payment is completed or cancelled.
			product.setStock(product.getStock() - itemRequest.getQuantity());
			productRepository.save(product);

			OrderItem item = new OrderItem();
			item.setOrder(order);
			item.setProduct(product);
			item.setQuantity(itemRequest.getQuantity());
			item.setUnitPrice(product.getPrice());
			order.getItems().add(item);

			total = total.add(product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
		}

		order.setTotalAmount(total);
		Order savedOrder = orderRepository.save(order);

		String razorpayOrderId = razorpayService.createOrder(savedOrder.getTotalAmount(), savedOrder.getId());

		savedOrder.setRazorpayOrderId(razorpayOrderId);
		orderRepository.save(savedOrder);

		CheckoutResponse response = new CheckoutResponse(toResponse(savedOrder), razorpayOrderId,
				razorpayService.getKeyId(), razorpayService.toPaise(savedOrder.getTotalAmount()), "INR");

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/{id}/verify-payment")
	@Transactional
	public OrderResponse verifyPayment(@PathVariable Long id, @Valid @RequestBody PaymentVerificationRequest request,
			Authentication auth) {

		AppUser user = currentUser(auth);

		Order order = orderRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

		if (!order.getUser().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your order");
		}

		if (order.getStatus() == Order.OrderStatus.PAID) {
			if (request.getRazorpayPaymentId().equals(order.getRazorpayPaymentId())) {
				return toResponse(order);
			}
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has already been paid");
		}

		if (order.getStatus() != Order.OrderStatus.PENDING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be paid");
		}

		if (!order.getRazorpayOrderId().equals(request.getRazorpayOrderId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay order ID does not match this order");
		}

		boolean signatureIsValid = razorpayService.verifyPaymentSignature(order.getRazorpayOrderId(),
				request.getRazorpayPaymentId(), request.getRazorpaySignature());

		if (!signatureIsValid) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature verification failed");
		}

		order.setRazorpayPaymentId(request.getRazorpayPaymentId());
		order.setStatus(Order.OrderStatus.PAID);
		order.setPaymentExpiresAt(null);

		return toResponse(orderRepository.save(order));
	}

	@GetMapping("/my")
	public List<OrderResponse> myOrders(Authentication auth) {
		AppUser user = currentUser(auth);

		return orderRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toResponse)
				.collect(Collectors.toList());
	}

	@GetMapping("/admin")
	public List<OrderResponse> allOrders() {
		return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse)
				.collect(Collectors.toList());
	}

	@PatchMapping("/admin/{id}/status")
	public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusUpdateRequest request) {

		Order order = orderRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

		Order.OrderStatus newStatus;
		try {
			newStatus = Order.OrderStatus.valueOf(request.getStatus().toUpperCase());
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + request.getStatus());
		}

		if (newStatus == Order.OrderStatus.CANCELLED && order.getStatus() == Order.OrderStatus.PENDING) {
			return toResponse(orderExpiryService.cancelPendingOrder(id));
		}

		order.setStatus(newStatus);
		return toResponse(orderRepository.save(order));
	}

	private AppUser currentUser(Authentication auth) {
		return appUserRepository.findByEmail(auth.getName())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
	}

	private OrderResponse toResponse(Order order) {
		List<OrderItemResponse> items = order
				.getItems().stream().map(item -> new OrderItemResponse(item.getProduct().getId(),
						item.getProduct().getName(), item.getQuantity(), item.getUnitPrice()))
				.collect(Collectors.toList());

		return new OrderResponse(order.getId(), order.getStatus().name(), order.getTotalAmount(),
				order.getShippingAddress(), order.getCreatedAt(), items);
	}
}