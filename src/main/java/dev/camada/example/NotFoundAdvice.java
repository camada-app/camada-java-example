package dev.camada.example;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The 404 page, rendered in the handler rather than through the container's error dispatch: the
 * filter stamped x-rid before the chain ran, and the same page shows the kill switch (no x-rid) and
 * the beacon endpoints standing down (the app's own 404 at /_cam/b.js).
 */
@RestControllerAdvice
class NotFoundAdvice {
  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<String> notFound(HttpServletRequest req) {
    return ShopController.page(req, "404", "<p>Nothing here.</p>", HttpStatus.NOT_FOUND);
  }
}
