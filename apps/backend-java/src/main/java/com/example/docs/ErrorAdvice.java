package com.example.docs;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorAdvice {
  @ExceptionHandler(UnauthorizedException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  ApiError unauthorized(UnauthorizedException ex) {
    return new ApiError("UNAUTHORIZED", ex.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  ApiError forbidden(ForbiddenException ex) {
    return new ApiError("FORBIDDEN", ex.getMessage());
  }

  @ExceptionHandler(BadRequestException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ApiError badRequest(BadRequestException ex) {
    return new ApiError("VALIDATION_ERROR", ex.getMessage());
  }

  @ExceptionHandler(EmptyResultDataAccessException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  ApiError notFound() {
    return new ApiError("NOT_FOUND", "Resource not found.");
  }
}

class UnauthorizedException extends RuntimeException {
  UnauthorizedException(String message) {
    super(message);
  }
}

class ForbiddenException extends RuntimeException {
  ForbiddenException(String message) {
    super(message);
  }
}

class BadRequestException extends RuntimeException {
  BadRequestException(String message) {
    super(message);
  }
}
