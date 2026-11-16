package com.bpbuilder.bp_builder.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Local-development CORS only: the frontend runs on a different origin than
 * this API (a Figma preview URL, a local dev server on another port, ...).
 * Wide open on purpose - there is no auth and nothing here is sensitive.
 * Tighten this before this service is ever exposed outside local development.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*");
    }
}
