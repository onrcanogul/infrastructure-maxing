package com.inframaxing.paymentservice.exception;

import com.inframaxing.paymentservice.controller.PaymentController;
import com.inframaxing.paymentservice.metrics.PaymentMetrics;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private final PaymentMetrics paymentMetrics;

	public ApiExceptionHandler(PaymentMetrics paymentMetrics) {
		this.paymentMetrics = paymentMetrics;
	}

	@ExceptionHandler(PaymentNotFoundException.class)
	public ProblemDetail paymentNotFound(PaymentNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Payment not found", e.getMessage());
	}

	@ExceptionHandler(IdempotencyKeyConflictException.class)
	public ProblemDetail idempotencyKeyConflict(IdempotencyKeyConflictException e) {
		return problem(HttpStatus.CONFLICT, "Idempotency key conflict", e.getMessage());
	}

	@ExceptionHandler(PaymentVersionConflictException.class)
	public ProblemDetail paymentVersionConflict(PaymentVersionConflictException e) {
		return problem(HttpStatus.CONFLICT, "Payment modified concurrently", e.getMessage());
	}

	@ExceptionHandler(InvalidPaymentTransitionException.class)
	public ProblemDetail invalidPaymentTransition(InvalidPaymentTransitionException e) {
		return problem(HttpStatus.CONFLICT, "Invalid payment transition", e.getMessage());
	}

	@ExceptionHandler(ProviderCallFailedException.class)
	public ProblemDetail providerCallFailed(ProviderCallFailedException e) {
		HttpStatus status = e.timedOut() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
		return problem(status, "Provider call failed", e.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail invalidArgument(IllegalArgumentException e, WebRequest request) {
		countInvalidCreation(HttpStatus.BAD_REQUEST, request);
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		countInvalidCreation(statusCode, request);
		return super.handleExceptionInternal(ex, body, headers, statusCode, request);
	}

	private void countInvalidCreation(HttpStatusCode statusCode, WebRequest request) {
		if (statusCode.value() == HttpStatus.BAD_REQUEST.value()
				&& request instanceof ServletWebRequest servletRequest
				&& HttpMethod.POST.matches(servletRequest.getRequest().getMethod())
				&& PaymentController.BASE_PATH.equals(
						servletRequest.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))) {
			paymentMetrics.invalid();
		}
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
