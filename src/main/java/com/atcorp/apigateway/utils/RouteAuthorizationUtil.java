package com.atcorp.apigateway.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "api.gateway")
@Data
public class RouteAuthorizationUtil {

    private List<RouteAuth> routes;

    public Set<String> getRequiredRoles(String path) {
        return routes.stream()
                .filter(element -> pathMatches(path, element.getPath()))
                .map(RouteAuth::getRoles)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private boolean pathMatches(String actualPath, String pattern) {
        String regex = pattern.replace("/**", "/.*")
                .replace("/*", "/[^/]*")
                .replace("{id}", "[^/]+");
        return actualPath.matches(regex);
    }

    @Data
    public static class RouteAuth {
        private String path;
        private Set<String> roles = new HashSet<>();
    }
}
