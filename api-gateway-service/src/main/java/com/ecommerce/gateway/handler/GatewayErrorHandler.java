package com.ecommerce.gateway.handler;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException responseStatusException) {
            status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            message = responseStatusException.getReason() != null
                    ? responseStatusException.getReason()
                    : status.getReasonPhrase();
        } else {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Service temporarily unavailable";
        }

        String body = String.format("{\"error\":\"%s\",\"status\":%d}", message, status.value());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
