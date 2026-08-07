package org.sterl.llmpeon.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.WriteValidator;

/**
 * Headless unit test (no Eclipse workspace needed) for the write-path validator on the Eclipse
 * workspace write tool: the {@code DOCS} validator rejects a non-docs path <em>before</em> any
 * Eclipse API is touched. Feature: docs/write-path-validator.md — Rule R1.
 */
public class EclipseWriteValidatorUnitTest {

    private ToolLoopRequest docsRequest() {
        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .writeValidator(WriteValidator.DOCS)
                .build();
    }

    @Test
    public void test_writeRejectedOutsideDocs() {
        var tool = new EclipseWorkspaceWriteFileTool();
        tool.withToolRequest(docsRequest());
        try {
            tool.eclipseWriteFile("MyProject/src/Foo.java", "x");
            fail("expected rejection for a non-docs path");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Write denied"));
        }
    }

    @Test
    public void test_deleteRejectedOutsideDocs() {
        var tool = new EclipseWorkspaceWriteFileTool();
        tool.withToolRequest(docsRequest());
        try {
            tool.eclipseDeleteResource("MyProject/src/Foo.java");
            fail("expected rejection for a non-docs path");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Write denied"));
        }
    }
}
