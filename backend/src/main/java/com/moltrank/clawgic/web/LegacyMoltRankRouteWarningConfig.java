package com.moltrank.clawgic.web;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers a warn-only interceptor for legacy MoltRank APIs so Clawgic demos
 * remain on the intended path without disabling existing endpoints yet.
 */
@Configuration
public class LegacyMoltRankRouteWarningConfig implements WebMvcConfigurer {

    private final ClawgicRuntimeProperties clawgicRuntimeProperties;

    public LegacyMoltRankRouteWarningConfig(ClawgicRuntimeProperties clawgicRuntimeProperties) {
        this.clawgicRuntimeProperties = clawgicRuntimeProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LegacyMoltRankRouteWarningInterceptor(clawgicRuntimeProperties))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/clawgic/**");
    }
}
