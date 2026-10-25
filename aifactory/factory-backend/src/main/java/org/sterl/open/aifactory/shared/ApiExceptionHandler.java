package org.sterl.open.aifactory.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import org.sterl.open.aifactory.project.ProjectNotFoundException;

/**
 * Turns the domain exceptions into the single {@link ApiError} body. Deliberately no catch-all —
 * anything else keeps Spring's own status and the {@code /ui/**} static handling. Log <em>or</em>
 * throw, never both: the 400s are the caller's fault and travel up untouched.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Rejected input and failed clones — the git output is the message the user has to see (R-WP-4). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "request body must be a JSON object with a gitUrl");
    }

    /** A target that exists but is not what we expected (e.g. a non-git folder of that name). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ProjectNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), reason(status), e.getReason()));
    }

    private static ResponseEntity<ApiError> body(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message));
    }

    private static String reason(HttpStatusCode status) {
        return HttpStatus.resolve(status.value()) != null
                ? HttpStatus.resolve(status.value()).getReasonPhrase()
                : "error";
    }
}
