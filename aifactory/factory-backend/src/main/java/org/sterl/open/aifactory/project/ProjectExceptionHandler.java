package org.sterl.open.aifactory.project;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import org.sterl.open.aifactory.shared.ApiError;
import org.sterl.open.aifactory.shared.ApiErrorResponses;

/**
 * The {@code 404} of this component. It lives here, not in {@code shared}: an advice over a
 * package's own types belongs to that package, while {@link ApiError} — framework-only — stays in
 * {@code shared} (invariant in {@code aifactory/docs/aifactory-architektur.md}).
 *
 * <p>Answered through {@link ApiErrorResponses} because the live edges of {@code chat} reach this
 * exception too: they accept no JSON, and only a bodyless refusal keeps the 404 (C-8).</p>
 */
@RestControllerAdvice
public class ProjectExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiError> unknownProject(ProjectNotFoundException e, WebRequest request) {
        return ApiErrorResponses.json(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }
}
