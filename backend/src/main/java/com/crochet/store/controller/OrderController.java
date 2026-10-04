package com.crochet.store.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudinary.Cloudinary;
import com.crochet.store.dto.OrderDtos.CheckoutItem;
import com.crochet.store.dto.OrderDtos.CheckoutRequest;
import com.crochet.store.dto.OrderDtos.OrderItemResponse;
import com.crochet.store.dto.OrderDtos.OrderResponse;
import com.crochet.store.dto.OrderStatusUpdateRequest;
import com.crochet.store.entity.AppUser;
import com.crochet.store.entity.Order;
import com.crochet.store.entity.OrderItem;
import com.crochet.store.entity.Product;
import com.crochet.store.repository.AppUserRepository;
import com.crochet.store.repository.OrderRepository;
import com.crochet.store.repository.ProductRepository;
import com.crochet.store.service.OrderExpiryService;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderRepository orderRepository;
	private final ProductRepository productRepository;
	private final AppUserRepository appUserRepository;
	private final OrderExpiryService orderExpiryService;
	private final Cloudinary cloudinary;

	public OrderController(OrderRepository orderRepository, ProductRepository productRepository,
			AppUserRepository appUserRepository, OrderExpiryService orderExpiryService, Cloudinary cloudinary) {

		this.orderRepository = orderRepository;
		this.productRepository = productRepository;
		this.appUserRepository = appUserRepository;
		this.orderExpiryService = orderExpiryService;
		this.cloudinary = cloudinary;
	}

	// Checkout for direct UPI QR payments.
	@PostMapping("/manual-upi-checkout")
	@Transactional
	public ResponseEntity<OrderResponse> manualUpiCheckout(@Valid @RequestBody CheckoutRequest request,
			Authentication auth) {

		Order savedOrder = createPendingOrder(request, currentUser(auth));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(savedOrder));
	}

	// Customer submits the UTR / transaction ID after paying the UPI QR.
	// Screenshot is optional and stored as an authenticated Cloudinary image.
	@PostMapping(value = "/{id}/submit-payment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Transactional
	public OrderResponse submitPayment(@PathVariable Long id, @RequestParam String transactionId,
			@RequestParam(required = false) MultipartFile screenshot, Authentication auth) {

		AppUser user = currentUser(auth);

		if (transactionId == null || transactionId.trim().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter the UPI transaction ID / UTR.");
		}

		Order order = orderRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

		if (!order.getUser().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your order");
		}

		if (order.getStatus() != Order.OrderStatus.PENDING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Payment details can only be submitted for a pending order");
		}

		order.setPaymentReference(transactionId.trim());

		if (screenshot != null && !screenshot.isEmpty()) {
			String contentType = screenshot.getContentType();

			if (contentType == null || !contentType.startsWith("image/")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The payment screenshot must be an image.");
			}

			try {
				Map<?, ?> uploadResult = cloudinary.uploader().upload(screenshot.getBytes(), ObjectUtils.asMap("folder",
						"crochet-store/payment-proofs", "resource_type", "image", "type", "authenticated"));

				order.setPaymentProofPublicId(String.valueOf(uploadResult.get("public_id")));
			} catch (IOException exception) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
						"Could not upload the payment screenshot.");
			}
		}

		order.setStatus(Order.OrderStatus.PAYMENT_SUBMITTED);
		order.setPaymentSubmittedAt(Instant.now());
		order.setPaymentExpiresAt(orderExpiryService.newPaymentReviewExpiry());

		return toResponse(orderRepository.save(order));
	}

	@PostMapping("/{id}/cancel")
	@Transactional
	public OrderResponse cancelOwnOrder(@PathVariable Long id, Authentication auth) {
		AppUser user = currentUser(auth);

		Order order = orderRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

		if (!order.getUser().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your order");
		}

		if (order.getStatus() != Order.OrderStatus.PENDING
				&& order.getStatus() != Order.OrderStatus.PAYMENT_SUBMITTED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unpaid orders can be cancelled");
		}

		return toResponse(orderExpiryService.cancelPendingOrder(id));
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

	private static final Map<Order.OrderStatus, java.util.Set<Order.OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
			Order.OrderStatus.PENDING,
			java.util.Set.of(Order.OrderStatus.PAYMENT_SUBMITTED, Order.OrderStatus.CANCELLED),

			Order.OrderStatus.PAYMENT_SUBMITTED, java.util.Set.of(Order.OrderStatus.PAID, Order.OrderStatus.CANCELLED),

			Order.OrderStatus.PAID, java.util.Set.of(Order.OrderStatus.SHIPPED, Order.OrderStatus.CANCELLED),

			Order.OrderStatus.SHIPPED, java.util.Set.of(Order.OrderStatus.DELIVERED),

			Order.OrderStatus.DELIVERED, java.util.Set.of(),

			Order.OrderStatus.CANCELLED, java.util.Set.of());

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

		Order.OrderStatus currentStatus = order.getStatus();

		if (newStatus == currentStatus) {
			return toResponse(order);
		}

		if (!ALLOWED_TRANSITIONS.getOrDefault(currentStatus, java.util.Set.of()).contains(newStatus)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Cannot change an order from " + currentStatus + " to " + newStatus);
		}

		if (newStatus == Order.OrderStatus.CANCELLED) {
			if (currentStatus == Order.OrderStatus.PENDING || currentStatus == Order.OrderStatus.PAYMENT_SUBMITTED) {
				return toResponse(orderExpiryService.cancelPendingOrder(id));
			}

			if (currentStatus == Order.OrderStatus.PAID) {
				return toResponse(orderExpiryService.cancelPaidOrder(id));
			}
		}

		if (newStatus == Order.OrderStatus.PAID) {
			order.setPaymentExpiresAt(null);
		}

		order.setStatus(newStatus);
		return toResponse(orderRepository.save(order));
	}

	private Order createPendingOrder(CheckoutRequest request, AppUser user) {
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
		return orderRepository.save(order);
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

		String proofUrl = null;

		if (order.getPaymentProofPublicId() != null) {
			proofUrl = cloudinary.url().secure(true).resourceType("image").type("authenticated").signed(true)
					.generate(order.getPaymentProofPublicId());
		}

		return new OrderResponse(order.getId(), order.getStatus().name(), order.getTotalAmount(),
				order.getShippingAddress(), order.getCreatedAt(), items, order.getPaymentReference(),
				order.getPaymentSubmittedAt(), proofUrl);
	}
}
