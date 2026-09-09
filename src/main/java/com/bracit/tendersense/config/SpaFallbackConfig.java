package com.bracit.tendersense.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * Serves the packaged Angular build and falls back to index.html for client-side
 * routes.
 *
 * <p>Without this a hard refresh on a deep link such as /tenders/1327991 returns 404:
 * Spring looks for a static file at that path and finds none. The failure never
 * appears under `ng serve` -- the dev server has its own fallback -- so it surfaces
 * for the first time in the packaged build, which is exactly when it must not.
 */
@Configuration
public class SpaFallbackConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new org.springframework.web.servlet.resource.PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // /api/** never reaches here: @RequestMapping wins over static
                        // resource handling, so this only catches client-side routes.
                        ClassPathResource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
