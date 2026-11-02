package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.project.Project;
import org.sterl.open.aifactory.project.ProjectService;
import org.sterl.open.aifactory.project.ProjectWork;
import org.sterl.open.aifactory.project.ProjectWorkStates;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * Reading a work state must not cost a conversation (R-WP-24): the project list is opened to look at the
 * registry, and a snapshot that built a runtime would hand every viewer of that list a Jon, a team and a
 * memory per project. So the port reads what is there and asks for nothing.
 */
@SpringBootTest
@Import(FakeGitConfig.class)
@Timeout(60)
class ProjectWorkStateTest {

    @TempDir
    static Path home;

    @Autowired
    private ProjectService projects;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    @Autowired
    private ProjectWorkStates workStates;

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add(FactoryHome.HOME_PROPERTY, home::toString);
    }

    @Test
    void readingTheStatusBuildsNoRuntime() {
        Project project = projects.add("https://git.example.com/acme/peek-47.git").project();

        assertThat(workStates.current()).containsExactly(ProjectWork.free(project.id()));
        assertThat(runtimes.runtimeCount())
                .as("der Blick auf die Liste legt nichts an — kein Jon, kein Team, kein Gedächtnis")
                .isZero();
        assertThat(home.resolve(FactoryHome.STATE_DIR))
                .as("und er schreibt nichts in den Bestand")
                .doesNotExist();
    }
}
