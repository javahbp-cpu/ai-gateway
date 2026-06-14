package io.github.portfolio.aigateway.error;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class GlobalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @ExceptionHandler(GatewayException.class)
    ResponseEntity<Map<String, Object>> handleGatewayException(GatewayException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
                "error", Map.of(
                        "code", exception.getCode(),
                        "message", exception.getMessage(),
                        "type", "gateway_error")));
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<Map<String, Object>> handleInvalidRequest(ServerWebInputException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, "invalid_request", "请求参数格式错误");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception exception) {
        log.error("网关发生未处理异常", exception);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "网关内部错误");
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of(
                        "code", code,
                        "message", message,
                        "type", "gateway_error")));
    }
}
