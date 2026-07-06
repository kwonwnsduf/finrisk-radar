package com.finrisk.radar.global.error;

import com.finrisk.radar.global.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity
				.status(errorCode.getHttpStatus())
				.body(ApiResponse.error(errorCode, null));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
			MethodArgumentNotValidException exception
	) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}

		ErrorCode errorCode = ErrorCode.INVALID_INPUT;
		return ResponseEntity
				.status(errorCode.getHttpStatus())
				.body(ApiResponse.error(errorCode, errors));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception) {
		ErrorCode errorCode = ErrorCode.INVALID_INPUT;
		return ResponseEntity
				.status(errorCode.getHttpStatus())
				.body(ApiResponse.error(errorCode, null));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		ErrorCode errorCode = ErrorCode.INVALID_INPUT;
		return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.error(errorCode, null));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		log.error("Unhandled exception", exception);
		ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
		return ResponseEntity
				.status(errorCode.getHttpStatus())
				.body(ApiResponse.error(errorCode, null));
	}
}
