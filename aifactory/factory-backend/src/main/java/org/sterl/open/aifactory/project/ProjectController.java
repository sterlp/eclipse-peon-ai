package org.sterl.open.aifactory.project;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sterl.open.aifactory.project.dto.AddProjectRequest;
import org.sterl.open.aifactory.project.dto.ProjectDto;

/**
 * Project registry over HTTP. Clone is synchronous, so the {@code 201} is only sent after the
 * checkout exists on disk (UC-WP-2) — a failed clone answers {@code 400} with the git output and
 * the list stays unchanged (UC-WP-4).
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    public List<ProjectDto> list() {
        return projects.projects().stream().map(ProjectDto::of).toList();
    }

    @PostMapping
    public ResponseEntity<ProjectDto> add(@RequestBody(required = false) AddProjectRequest request) {
        AddProjectResult result = projects.add(request == null ? null : request.gitUrl());
        // a url that is already registered is reused, not rejected — 200, nothing new was created (UC-WP-3)
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ProjectDto.of(result.project()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable("id") String id) {
        projects.remove(id); // ProjectNotFoundException -> 404
    }
}
