package com.example.searchapp.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

/**
 * Configures static resource serving for the React SPA. Spring Boot serves {@code index.html} from
 * {@code classpath:/static/} for {@code /} by default. This resolver additionally serves {@code
 * index.html} for SPA client-side routes when navigating directly to them, but only for paths that
 * look like SPA routes (start with {@code /clients/}) and have no file extension.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(false)
        .addResolver(new SpaResourceResolver());
  }

  private static class SpaResourceResolver extends PathResourceResolver {

    @Override
    protected Resource resolveResourceInternal(
        HttpServletRequest request,
        String requestPath,
        List<? extends Resource> locations,
        ResourceResolverChain chain) {

      Resource resource = super.resolveResourceInternal(request, requestPath, locations, chain);
      if (resource != null) {
        return resource;
      }

      // Only fall back to index.html for GET requests to SPA client-side routes.
      if (!HttpMethod.GET.matches(request.getMethod())) {
        return null;
      }
      if (!isSpaRoute(requestPath)) {
        return null;
      }

      for (Resource location : locations) {
        try {
          Resource indexResource = getResource("index.html", location);
          if (indexResource != null && indexResource.exists()) {
            return indexResource;
          }
        } catch (IOException e) {
          // fall through to the next location
        }
      }
      return null;
    }

    /** Returns true for paths that look like SPA client-side routes. */
    private static boolean isSpaRoute(String path) {
      // SPA routes have no file extension
      if (path.contains(".")) {
        return false;
      }
      // Only known SPA routes
      return path.startsWith("clients/") || path.equals("clients");
    }
  }
}
