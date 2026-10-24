package org.sterl.llmpeon.tool.tools;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Pure HTML → hit mapping of the DuckDuckGo HTML endpoint — no I/O, so the whole layout contract
 * (org.sterl.llmpeon.core/docs/web-tools.md R-WEB-10) stays reachable through the engine's tests.
 * <p>
 * Package-private on purpose: the engine is the only caller, the engine's test the only seam.
 */
final class DuckDuckGoHtmlParser {

    record Hit(String title, URI url, String snippet) {}

    private DuckDuckGoHtmlParser() {}

    /**
     * @throws WebSearchException when the page is a bot check or a layout that can no longer be
     *         read — never for a genuinely empty result set (R-WEB-12)
     */
    static List<Hit> parse(String html) {
        String raw = html == null ? "" : html;
        Document doc = Jsoup.parse(raw);

        List<Hit> hits = new ArrayList<>();
        for (Element result : doc.select("div.result").not(".result--ad")) {
            Hit hit = toHit(result);
            if (hit != null) hits.add(hit);
        }
        return hits.isEmpty() ? classifyEmptyPage(doc, raw.toLowerCase()) : List.copyOf(hits);
    }

    /**
     * A page without one readable hit is either an honest zero, a bot check or a layout drift —
     * those must stay distinguishable (R-WEB-12). The bot check is checked first on purpose:
     * reporting "0 results" for a blocked request is exactly the false negative R-WEB-12 forbids.
     */
    private static List<Hit> classifyEmptyPage(Document doc, String lowerCasedHtml) {
        if (isBotCheck(doc)) throw new WebSearchException("bot-check page");
        if (lowerCasedHtml.contains("no results")) return List.of();
        throw new WebSearchException("layout not recognized (0 parseable results)");
    }

    /**
     * The bot check is its modal element, never the word "anomaly" somewhere in the answer: the
     * endpoint echoes the query into the no-results page, so searching for "anomaly detection" would
     * otherwise end as a bot-check error instead of the honest zero R-WEB-12 asks for. The live
     * challenge names it {@code .anomaly-modal__mask} / {@code [data-testid=anomaly-modal]} and puts
     * "anomaly" only in attributes besides — a bare {@code .anomaly-modal} class selector would never
     * fire. Only a modal with text is evidence: a shell shipped empty with every page must not kill
     * the zero path either. HTTP 202 remains the primary bot-check signal, classified on status in
     * the engine before any parsing.
     */
    private static boolean isBotCheck(Document doc) {
        Element modal = doc.selectFirst("[class*=anomaly-modal], [data-testid=anomaly-modal]");
        return modal != null && !modal.text().isBlank();
    }

    /** A hit without title or without an absolute http(s) target is skipped, not fatal (R-WEB-10). */
    private static Hit toHit(Element result) {
        Element link = result.selectFirst("a.result__a");
        if (link == null) return null;

        String title = link.text().strip();
        URI target = targetUrl(link.attr("href"));
        if (title.isEmpty() || target == null) return null;

        return new Hit(title, target, snippet(result));
    }

    private static String snippet(Element result) {
        Element snippet = result.selectFirst("a.result__snippet");
        if (snippet == null) snippet = result.selectFirst(".result__snippet");
        if (snippet == null) return null;

        String text = snippet.text().strip();
        return text.isEmpty() ? null : text;
    }

    /** DuckDuckGo wraps outbound links as {@code /l/?uddg=<urlencoded target>} — the target is the hit. */
    private static URI targetUrl(String href) {
        String target = href == null ? "" : href.strip();
        if (target.startsWith("//")) target = "https:" + target;

        String wrapped = queryValue(target, "uddg");
        if (wrapped != null && !wrapped.isBlank()) {
            target = URLDecoder.decode(wrapped, StandardCharsets.UTF_8);
        }
        return absoluteHttpUri(target);
    }

    private static String queryValue(String url, String name) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) return null;
        for (String pair : url.substring(queryStart + 1).split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0 && pair.substring(0, separator).equals(name)) {
                return pair.substring(separator + 1);
            }
        }
        return null;
    }

    private static URI absoluteHttpUri(String candidate) {
        try {
            URI uri = new URI(candidate);
            boolean absoluteHttp = uri.isAbsolute()
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
            return absoluteHttp ? uri : null;
        } catch (URISyntaxException | IllegalArgumentException notAUrl) {
            return null;
        }
    }
}
