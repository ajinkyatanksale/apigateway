package com.atcorp.apigateway.configurations;

import com.atcorp.apigateway.utils.JwtHelper;
import com.atcorp.apigateway.utils.RouteAuthorizationUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.List;



@Component
public class JwtAuthenticationFilter implements GatewayFilter {

    private final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    JwtHelper jwtHelper;

    @Autowired
    RouteAuthorizationUtil routeAuthorizationUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

            String token = getTokenFromRequest(exchange.getRequest());
            String username = null;
            if (StringUtils.isNotBlank(token)) {
                try {
                    username = jwtHelper.getUsernameFromToken(token);
                } catch (IllegalArgumentException e) {
                    logger.info("Illegal Argument while fetching the username !!");
                    logger.error(e.getMessage());
                } catch (ExpiredJwtException e) {
                    logger.info("Given jwt token is expired !!");
                    logger.error(e.getMessage());
                } catch (MalformedJwtException e) {
                    logger.info("Some changes has done in token !! Invalid Token");
                    logger.error(e.getMessage());
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }

            if (!jwtHelper.validateToken(token, username)) {
                return getErrorResponse(exchange);
            }

            String role = jwtHelper.getUserRoleFromToken(token);
            if (!authorizeUserBasedOnRole(role, exchange.getRequest().getPath().value())) {
                return getErrorResponse(exchange);
            }

        ServerWebExchange modifiedExchange = getModifiedExchange(exchange, token);

        return chain.filter(modifiedExchange);
    }

    private static Mono<Void> getErrorResponse(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(403));
        String body = """
                {
                    "error": "Forbidden",
                    "message": "%s",
                    "status": 403,
                    "timestamp": "%s"
                }
            """.formatted("The jwt token validation failed", Instant.now());

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        exchange.getResponse().writeWith(Mono.just(buffer));
        return exchange.getResponse().setComplete();
    }

    private ServerWebExchange getModifiedExchange(ServerWebExchange exchange, String token) {
        logger.info("{}", exchange.getRequest().getPath().value());
        ServerWebExchange modifiedExchange = exchange;
        if (exchange.getRequest().getPath().value().contains("/manage/seats") ||
                exchange.getRequest().getPath().value().contains("/user/details")) {
            logger.info("Adding userId to URI");
            Long userId = jwtHelper.getUserIdFromToken(token);
            URI newUri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                    .queryParam("userId", userId)
                    .build()
                    .toUri();
            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .uri(newUri)
                    .build();

            modifiedExchange = exchange.mutate()
                    .request(modifiedRequest)
                    .build();
        }
        return modifiedExchange;
    }

    private String getTokenFromRequest(ServerHttpRequest request) {
        List<String> requestHeader = request.getHeaders().get("Authorization");
        String token = null;
        if (!CollectionUtils.isEmpty(requestHeader)) {
            if (requestHeader.get(0).startsWith("Bearer")) {
                token = requestHeader.get(0).substring(7);
            } else {
                logger.info("Invalid Header Value !! ");
            }
        }
        return token;
    }

    private boolean authorizeUserBasedOnRole(String role, String path) {
        return routeAuthorizationUtil.getRequiredRoles(path).contains(role);
    }
}
