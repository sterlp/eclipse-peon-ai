package org.sterl.open.aifactory.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test stand-in for git: records the clone targets and creates the checkout folder, so "201 only
 * after the clone" and "remove deletes the checkout" are provable without git or network. A test that
 * needs a failing clone sets {@link #failWith} — the fake then throws <em>before</em> creating
 * anything, like the real connector after its cleanup.
 */
@TestConfiguration
public class FakeGitConfig {

    public final List<Path> cloneTargets = new ArrayList<>();
    public volatile RuntimeException failWith;

    @Bean
    @Primary
    public GitService fakeGitService() {
        return (gitUrl, target) -> {
            cloneTargets.add(target);
            if (failWith != null) {
                throw failWith;
            }
            try {
                Files.createDirectories(target.resolve(".git"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return true;
        };
    }
}
