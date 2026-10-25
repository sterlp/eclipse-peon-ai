package org.sterl.open.aifactory.ui;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The entry point of the browser product: {@code /ui} and {@code /ui/} answer the built
 * {@code index.html} as itself — 200, {@code text/html}, the bytes shipped by {@code factory-ui}
 * (R-WP-22).
 *
 * <p>Deliberately not a view: the returned body leaves through the message converter, so no view name
 * is ever resolved and nothing can be forwarded onto the address being served — a view name derived
 * from the request path is what once turned every {@code /ui/**} call into a thousands-deep forward
 * chain. The resource handler cannot take this path either: it discards an empty path-within-mapping
 * before its resolvers run.</p>
 *
 * <p>Only these two addresses are mapped, so {@code /ui/assets/**} and {@code /ui/index.html} stay with
 * {@link ResourceUIConfig}. There is no fallback for unknown sub-paths: the MVP has no client-side
 * router, so there is no route to rescue (open-points: <em>kein SPA-Routing im MVP</em>).</p>
 */
@Controller
public class SpaIndexController {

    private static final Resource INDEX_HTML = new ClassPathResource("static/ui/index.html");

    @GetMapping({ "/ui", "/ui/" })
    public ResponseEntity<Resource> index() {
        // content type stated, not sniffed: this file is the app, whatever the container thinks of .html
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(INDEX_HTML);
    }
}
