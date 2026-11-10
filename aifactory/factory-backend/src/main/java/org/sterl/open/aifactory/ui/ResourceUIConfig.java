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
 * in {@link SpaIndexController}; a sub-path that is no file is answered by {@link SpaFallbackResolver},
 * and {@code /} only redirects here.
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

        // the resolver replaces the plain path lookup for this handler only: an address that is no file is
        // the app (UC-WP-43), a missing file with an extension stays a 404 — assets keep their own handler
        // and their own cache header, this chain caches nothing but the resolved HTML of /ui/**
        registry.addResourceHandler(BASE + "/**")
                .addResourceLocations("classpath:/static" + BASE + "/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }
}
