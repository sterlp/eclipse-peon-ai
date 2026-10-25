package org.sterl.open.aifactory.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test stand-in for git: records the clone targets and creates the checkout folder, so "201 only
 * after the clone" and "remove deletes the checkout" are provable without git or network.
 */
@TestConfiguration
public class FakeGitConfig {

    public final List<Path> cloneTargets = new ArrayList<>();
    public volatile boolean failNext;

    @Bean
    @org.springframework.context.annotation.Primary
    public GitService fakeGitService() {
        return (gitUrl, target) -> {
            cloneTargets.add(target);
            if (failNext) {
                failNext = false;
                throw new CloneFailedException(gitUrl, target,
                        "fatal: repository 'https://git.example.com/acme/gone.git' not found");
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
