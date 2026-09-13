package com.inframaxing.paymentservice.api;

import com.inframaxing.paymentservice.application.IdempotencyService;
import com.inframaxing.paymentservice.domain.PaymentNotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(PaymentNotFound.class)
	public ProblemDetail paymentNotFound(PaymentNotFound e) {
		return problem(HttpStatus.NOT_FOUND, "Payment not found", e.getMessage());
	}

	@ExceptionHandler(IdempotencyService.KeyReusedException.class)
	public ProblemDetail idempotencyKeyReused(IdempotencyService.KeyReusedException e) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency key reused", e.getMessage());
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
