package com.atcorp.apigateway.configurations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegisterRoutes {

    @Autowired
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public RouteLocator myRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(p -> p
                        .path("/users/find")
                        .filters(f -> f.filter(jwtAuthenticationFilter))
                        .uri("http://localhost:8081"))
                .route(p -> p
                        .path("/manage/**", "/user/**")
                        .filters(f -> f.filter(jwtAuthenticationFilter))
                        .uri("http://localhost:8082"))
                .build();
    }

}
