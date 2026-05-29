package org.sterl.llmpeon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.ConfiguredModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.UserMessage;

public class AiDeveloperServiceTest {

    @Test
    void testStandingOrder() throws IOException {
        AiDeveloperService subject = new AiDeveloperService(new ConfiguredModel(LlmConfig.newOllama("foo")), new ToolService(), 
                new SkillService(Path.of(".")));
        
        subject.setUserContextInformations(List.of());
        
        assertThat(subject.getUserContextInformations()).isEmpty();
    }
    
    @Test
    void testAddStandingOrder() throws IOException {
        AiDeveloperService subject = new AiDeveloperService(new ConfiguredModel(LlmConfig.newOllama("foo")), new ToolService(), 
                new SkillService(Path.of(".")));
        
        subject.setUserContextInformations(List.of(UserMessage.from("Foo")));
        
        assertThat(subject.getUserContextInformations()).hasSize(1);
    }
}
