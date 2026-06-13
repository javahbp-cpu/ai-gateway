package io.github.portfolio.aigateway.error;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(GatewayException.class)
    ResponseEntity<Map<String, Object>> handleGatewayException(GatewayException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
                "error", Map.of(
                        "code", exception.getCode(),
                        "message", exception.getMessage(),
                        "type", "gateway_error")));
    }
}
