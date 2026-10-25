package org.sterl.llmpeon.tool.web;

/**
 * The single error channel of a web search engine (org.sterl.llmpeon.core/docs/web-tools.md
 * R-WEB-12, docs/adr/0074-websearch-eigener-ddg-client.md): a search either returns results or
 * throws this — so "the endpoint did not answer" can never be mistaken for "there was nothing".
 * The message names the cause and is shown to the agent as is.
 */
public class WebSearchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WebSearchException(String message) {
        super(message);
    }

    public WebSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
