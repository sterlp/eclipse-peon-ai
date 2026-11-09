package org.sterl.open.aifactory.shared;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns the JDK and framework exceptions into the single {@link ApiError} body. Deliberately no
 * catch-all — anything else keeps Spring's own status and the {@code /ui/**} static handling. Log
 * <em>or</em> throw, never both: the 400s are the caller's fault and travel up untouched.
 *
 * <p>Every answer goes out through {@link ApiErrorResponses}, which is also what decides whether a body
 * may be written at all — a refusal of a live edge has to keep its status (C-8).</p>
 *
 * <p>JDK and framework types only. A component's own exceptions are mapped by an advice in that
 * component's package — {@code shared} must not know a component
 * ({@code aifactory/docs/aifactory-architektur.md}).</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Rejected input and failed clones — the git output is the message the user has to see (R-WP-4). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException e, WebRequest request) {
        return ApiErrorResponses.json(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e, WebRequest request) {
        return ApiErrorResponses.json(HttpStatus.BAD_REQUEST,
                "request body must be a JSON object with a gitUrl", request);
    }

    /** A target that exists but is not what we expected (e.g. a non-git folder of that name). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException e, WebRequest request) {
        return ApiErrorResponses.json(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException e, WebRequest request) {
        return ApiErrorResponses.json(e.getStatusCode(), e.getReason(), request);
    }

    /**
     * A request body the Bean Validation of its own shape refused (R-WP-55, UC-WP-92): one entry per
     * field, in the order the validator found them, each naming the property it rejects — and the
     * <em>first</em> reason of a field wins, so one wrong value never becomes two lines for one field.
     * Nothing is checked here: the rules live on the shape, so a field is stated exactly once.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidFields(MethodArgumentNotValidException e, WebRequest request) {
        Map<String, ApiFieldError> firstReasonPerField = new LinkedHashMap<>();
        for (ObjectError violated : e.getBindingResult().getAllErrors()) {
            String field = violated instanceof FieldError rejected
                    ? rejected.getField()
                    : violated.getObjectName();
            firstReasonPerField.putIfAbsent(field, new ApiFieldError(field, violated.getDefaultMessage()));
        }
        return ApiErrorResponses.invalidFields(List.copyOf(firstReasonPerField.values()), request);
    }
}
