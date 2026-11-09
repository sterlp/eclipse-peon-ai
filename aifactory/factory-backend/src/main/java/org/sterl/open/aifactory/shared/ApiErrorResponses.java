package org.sterl.open.aifactory.shared;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

/**
 * The one way an error leaves the factory, so two decisions are made in one place instead of in every
 * advice: whether a body may be written at all, and what the {@link ApiError} says.
 *
 * <p>The first decision is what C-8 is about. A refused live edge answers {@code text/event-stream}; a
 * JSON body the caller cannot accept is turned into an {@code HttpMediaTypeNotAcceptableException}
 * <em>while the advice is answering</em>, and the caller loses the real status to a 500. An answer
 * without a body asks no converter at all, so the refusal keeps its status — paid for with the wording,
 * which a live client never reads: it acts on the status.</p>
 *
 * <p>JDK and framework types only, like {@link ApiExceptionHandler}.</p>
 */
public final class ApiErrorResponses {

    private ApiErrorResponses() {
    }

    /** The usual answer: the readable reason, and no field named — nothing of this body was validated. */
    public static ResponseEntity<ApiError> json(HttpStatusCode status, String message, WebRequest request) {
        return answer(status, message, null, request);
    }

    /**
     * A rejected body, every violation naming its own field (UC-WP-92). {@code message} is the first of
     * them and never a generic sentence: clients read the reason out of it, and a field's reason must not
     * have to be parsed back out of one line.
     */
    public static ResponseEntity<ApiError> invalidFields(List<ApiFieldError> fieldErrors, WebRequest request) {
        String first = fieldErrors.isEmpty()
                ? HttpStatus.BAD_REQUEST.getReasonPhrase()
                : fieldErrors.get(0).message();
        return answer(HttpStatus.BAD_REQUEST, first, fieldErrors, request);
    }

    private static ResponseEntity<ApiError> answer(HttpStatusCode status, String message,
            List<ApiFieldError> fieldErrors, WebRequest request) {
        if (!acceptsJson(request)) {
            return ResponseEntity.status(status).build();
        }
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), reason(status), message, fieldErrors));
    }

    /**
     * Whether a JSON body may be written: no header at all, a wildcard, JSON itself, or a {@code +json}
     * suffix. Anything else — {@code text/event-stream} above all — is answered bodyless rather than with
     * a type the caller refused. A header this parser rejects, and an empty one, are treated as JSON: that
     * is what Spring itself would have answered with.
     */
    private static boolean acceptsJson(WebRequest request) {
        String[] header = request.getHeaderValues(HttpHeaders.ACCEPT);
        if (header == null || header.length == 0) {
            return true;
        }
        try {
            List<MediaType> accepted = MediaType.parseMediaTypes(String.join(",", header));
            return accepted.isEmpty() || accepted.stream().anyMatch(MediaType.APPLICATION_JSON::isCompatibleWith)
                    || accepted.stream().anyMatch(type -> type.getSubtype().endsWith("+json"));
        } catch (InvalidMediaTypeException unreadable) {
            return true;
        }
    }

    private static String reason(HttpStatusCode status) {
        return HttpStatus.resolve(status.value()) != null
                ? HttpStatus.resolve(status.value()).getReasonPhrase()
                : "error";
    }
}
