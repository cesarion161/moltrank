package com.moltrank.clawgic.web;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tags legacy MoltRank API responses when Clawgic mode is enabled so demo operators
 * can easily spot accidental use of pre-pivot routes without breaking compatibility.
 */
public class LegacyMoltRankRouteWarningInterceptor implements HandlerInterceptor {

    public static final String LEGACY_ROUTE_HEADER = "X-Clawgic-Legacy-Route";

    private static final Logger log = LoggerFactory.getLogger(LegacyMoltRankRouteWarningInterceptor.class);

    private final ClawgicRuntimeProperties clawgicRuntimeProperties;
    private final Set<String> loggedRoutes = ConcurrentHashMap.newKeySet();

    public LegacyMoltRankRouteWarningInterceptor(ClawgicRuntimeProperties clawgicRuntimeProperties) {
        this.clawgicRuntimeProperties = clawgicRuntimeProperties;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!clawgicRuntimeProperties.isEnabled() || !clawgicRuntimeProperties.isLegacyMoltRankApiEnabled()) {
            return true;
        }

        String routeKey = request.getMethod() + " " + request.getRequestURI();
        response.setHeader(LEGACY_ROUTE_HEADER, "true");

        if (loggedRoutes.add(routeKey)) {
            log.warn("Legacy MoltRank API route hit while Clawgic mode is enabled: {}", routeKey);
        }

        return true;
    }
}
