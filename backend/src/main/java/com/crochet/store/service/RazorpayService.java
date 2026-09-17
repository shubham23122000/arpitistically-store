package com.crochet.store.service;

import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class RazorpayService {

	private final String keyId;
	private final String keySecret;

	public RazorpayService(@Value("${razorpay.key-id}") String keyId,
			@Value("${razorpay.key-secret}") String keySecret) {
		this.keyId = keyId;
		this.keySecret = keySecret;
	}

	public String createOrder(BigDecimal amountInRupees, Long internalOrderId) {
		ensureConfigured();

		try {
			JSONObject request = new JSONObject();
			request.put("amount", toPaise(amountInRupees));
			request.put("currency", "INR");
			request.put("receipt", "store_" + internalOrderId);

			com.razorpay.Order razorpayOrder = client().orders.create(request);
			return razorpayOrder.get("id").toString();
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create Razorpay order", exception);
		}
	}

	public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {

		ensureConfigured();

		try {
			JSONObject options = new JSONObject();
			options.put("razorpay_order_id", razorpayOrderId);
			options.put("razorpay_payment_id", razorpayPaymentId);
			options.put("razorpay_signature", razorpaySignature);

			return Utils.verifyPaymentSignature(options, keySecret);
		} catch (Exception exception) {
			return false;
		}
	}

	public String getKeyId() {
		return keyId;
	}

	public long toPaise(BigDecimal amountInRupees) {
		try {
			return amountInRupees.movePointRight(2).longValueExact();
		} catch (ArithmeticException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					"Product price must have at most two decimal places");
		}
	}

	private RazorpayClient client() throws Exception {
		return new RazorpayClient(keyId, keySecret);
	}

	private void ensureConfigured() {
		if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay keys are not configured");
		}
	}
}