package org.sterl.open.aifactory.project.dto;

import org.sterl.open.aifactory.project.HttpsGitUrl;
import org.sterl.open.aifactory.project.ProjectService;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/projects}. The url is the whole request, so both of its rules sit on the field
 * the page is bound to (R-WP-55): it must not be empty, and it must be a public {@code https} address
 * (R-WP-20). A refusal names that field and repeats no part of the url — no credential, no host
 * (UC-WP-92, UC-WP-30).
 */
public record AddProjectRequest(@NotBlank(message = ProjectService.GIT_URL_HINT) @HttpsGitUrl String gitUrl) {
}
