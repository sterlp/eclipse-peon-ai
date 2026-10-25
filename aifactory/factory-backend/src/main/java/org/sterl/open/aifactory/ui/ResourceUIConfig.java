package org.sterl.open.aifactory.ui;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Static delivery of the built SPA: hashed assets (immutable, so cacheable for ages) and everything
 * else under {@code /ui/}. The entry point {@code /ui} and {@code /ui/} is not a static path and lives
 * in {@link SpaIndexController}; {@code /} only redirects here.
 */
@Configuration
public class ResourceUIConfig implements WebMvcConfigurer {
    private static final String BASE = "/ui";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // a redirect, never a forward: R-WP-22 forbids a request ending in a chain of forwards
        registry.addRedirectViewController("/", BASE + "/");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(BASE + "/assets/**").addResourceLocations("classpath:/static" + BASE + "/assets/")
                .setCacheControl(CacheControl.maxAge(90, TimeUnit.DAYS));

        registry.addResourceHandler(BASE + "/**").addResourceLocations("classpath:/static" + BASE + "/");
    }
}
