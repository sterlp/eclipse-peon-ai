package org.sterl.open.aifactory.ui;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * The SPA's answer to an address that is no file (R-WP-22, UC-WP-43): under {@code /ui/} the path belongs
 * to the browser app, so an address whose last segment carries no file name is a route the client resolves
 * — and the answer is the app itself, the same bytes {@link SpaIndexController} answers for {@code /ui}.
 *
 * <p>What stays a file stays a file: a hashed asset that is missing is an error and never the app, because
 * HTML is not a module — that is why {@code assets/} is excluded rather than trusted to the handler order.
 * Nothing here redirects or forwards: the body leaves as a {@link ClassPathResource}, which is what keeps
 * the forward chain R-WP-22 forbids impossible (a view name derived from the request path is what caused
 * it once).</p>
 *
 * <p>{@code /ui} and {@code /ui/} never reach this resolver — the resource handler discards an empty path
 * within its mapping before any resolver runs, which is why those two addresses stay with the controller.
 * A path ending in {@code /} is answered as the app and never as a directory listing.</p>
 */
public class SpaFallbackResolver extends PathResourceResolver {

    private static final Resource INDEX_HTML = new ClassPathResource("static/ui/index.html");

    private static final String ASSETS = "assets/";

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        if (resourcePath.isBlank() || resourcePath.endsWith("/")) {
            return INDEX_HTML;
        }
        Resource file = super.getResource(resourcePath, location);
        if (file != null) {
            return file;
        }
        return isRoute(resourcePath) ? INDEX_HTML : null;
    }

    /** A path the client routes: no asset, and its last segment carries no file extension. */
    private static boolean isRoute(String resourcePath) {
        if (resourcePath.startsWith(ASSETS)) {
            return false;
        }
        String lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        return !lastSegment.contains(".");
    }
}
