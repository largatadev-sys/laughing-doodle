package com.largatadev.timesheet.web;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the bundled Expo web export (web.output: "single", an SPA) from
 * {@code classpath:/static/}, with a fallback to {@code index.html} for client-side routes —
 * e.g. a hard refresh on {@code /team} or {@code /42/edit}, which have no matching file.
 * See ADR-008.
 *
 * <p>Registering {@code /**} here makes Spring Boot skip its own default {@code /**} static
 * handler, so this handler serves every non-API path. The API is never served here:
 * {@code /api/**} is owned by controllers (which take precedence over resource handlers), and
 * the resolver additionally refuses the SPA fallback for {@code api/*} paths and for missing
 * files that look like assets (a name containing a dot) — so a broken bundle reference 404s
 * visibly instead of being masked by the HTML shell.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

	private static final Resource INDEX = new ClassPathResource("static/index.html");

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
				.addResourceLocations("classpath:/static/")
				.resourceChain(true)
				.addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(String resourcePath, Resource location) throws IOException {
						Resource requested = location.createRelative(resourcePath);
						if (requested.exists() && requested.isReadable()) {
							return requested;
						}
						// Never mask an API path or a missing asset with the SPA shell.
						if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
							return null;
						}
						// A client-side route: let expo-router resolve it from index.html.
						return INDEX.exists() ? INDEX : null;
					}
				});
	}
}
