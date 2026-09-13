package com.inframaxing.paymentservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

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

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail invalidArgument(IllegalArgumentException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
