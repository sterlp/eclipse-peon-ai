package org.sterl.open.aifactory.project;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.sterl.open.aifactory.shared.ApiError;

/**
 * The {@code 404} of this component. It lives here, not in {@code shared}: an advice over a
 * package's own types belongs to that package, while {@link ApiError} — framework-only — stays in
 * {@code shared} (invariant in {@code aifactory/docs/aifactory-architektur.md}).
 */
@RestControllerAdvice
public class ProjectExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiError> unknownProject(ProjectNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(),
                        HttpStatus.NOT_FOUND.getReasonPhrase(), e.getMessage()));
    }
}
