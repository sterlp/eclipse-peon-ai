package org.sterl.open.aifactory.config;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sterl.open.aifactory.config.dto.AgentSettingsDto;
import org.sterl.open.aifactory.config.dto.AgentSettingsUpdateRequest;

/**
 * The agent overrides over HTTP (R-WP-23). Two endpoints, one slot per write: the default level stays a
 * small payload, and a request that carries seven slots makes a rejection undistinguishable from a
 * success on another one.
 *
 * <p>As on the default level, a write answers with a fresh read of the file and never with the submitted
 * body — so the page cannot show a value the file refused to keep.</p>
 */
@RestController
@RequestMapping("/api/settings/agents")
public class AgentSettingsController {

    private final AgentSettingsService agents;

    public AgentSettingsController(AgentSettingsService agents) {
        this.agents = agents;
    }

    @GetMapping
    public List<AgentSettingsDto> list() {
        return agents.list().stream().map(AgentSettingsDto::of).toList();
    }

    /** The only place an inbound agent key is translated away — it stops in the internal facade. */
    @PutMapping("/{id}")
    public AgentSettingsDto update(@PathVariable("id") String id,
            @RequestBody AgentSettingsUpdateRequest request) {
        return AgentSettingsDto.of(agents.update(id, request.toUpdate()));
    }
}
