package org.sterl.open.aifactory.ui;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SpringPersistentTasksUIConfig implements WebMvcConfigurer {
    private static final String BASE = "/ui";
    private static final String INDEX_HTML = "forward:/ui/index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(BASE + "/assets/**").addResourceLocations("classpath:/static" + BASE + "/assets/")
                .setCacheControl(CacheControl.maxAge(90, TimeUnit.DAYS));

        registry.addResourceHandler(BASE + "/**").addResourceLocations("classpath:/static" + BASE + "/");
    }
}
