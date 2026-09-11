package com.andface.backend.exception;

import com.andface.backend.common.ApiError;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> api(ApiException e) {
    return ResponseEntity.status(e.status)
        .body(ApiError.of(e.status.value(), e.code, e.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    MethodValidationException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class
  })
  ResponseEntity<ApiError> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(ApiError.of(400, "VALIDATION_FAILED", "Invalid request fields or format"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> conflict(Exception e) {
    return ResponseEntity.status(409)
        .body(ApiError.of(409, "CONFLICT", "A conflicting record already exists"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> denied(Exception e) {
    return ResponseEntity.status(403).body(ApiError.of(403, "FORBIDDEN", "Access denied"));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ApiError> notFound(Exception e) {
    return ResponseEntity.status(404).body(ApiError.of(404, "NOT_FOUND", "Endpoint not found"));
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> method(Exception e) {
    return ResponseEntity.status(405)
        .body(ApiError.of(405, "METHOD_NOT_ALLOWED", "HTTP method not allowed"));
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiError> media(Exception e) {
    return ResponseEntity.status(415)
        .body(ApiError.of(415, "UNSUPPORTED_MEDIA_TYPE", "JSON content type required"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e) {
    LoggerFactory.getLogger(getClass())
        .error("Unhandled request failure: {}", e.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .body(ApiError.of(500, "INTERNAL_ERROR", "Request could not be completed"));
  }
}
