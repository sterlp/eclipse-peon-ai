package org.sterl.llmpeon.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.ThinkResolver;
import org.sterl.llmpeon.ai.model.AiModel;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;

/** Ollama provider (stateless singleton). */
public final class OllamaProvider implements LlmProvider {

    @Override
    public StreamingChatModel buildModel(LlmConfig c) {
        // Thinking is now set per request (see newRequestParameters). returnThinking stays
        // build-time (langchain4j has no per-request setter) and is always on so thinking is
        // parsed whenever a per-agent think value enables it.
        var builder = OllamaStreamingChatModel.builder()
                .timeout(c.getTimeout())
                .baseUrl(c.getUrl())
                .modelName(c.getModel())
                .returnThinking(Boolean.TRUE)
                .customHeaders(c.getHeaderParams())
                .logRequests(c.isDebugMode())
                .logResponses(c.isDebugMode());
        if (c.getMaxTokens() > 0) builder.numPredict(c.getMaxTokens());
        return builder.build();
    }

    @Override
    public ChatRequestParameters newRequestParameters(AgentConfig mc, List<ToolSpecification> tools) {
        var b = OllamaChatRequestParameters.builder();
        ProviderRequestSupport.applyBase(b, mc, tools);
        // unset/null omits; resolved off/empty sends think:false; else think:true
        var think = ThinkResolver.toOllamaThink(mc.getThink());
        if (think != null) b.think(think);
        return b.build();
    }

    @Override
    public List<AiModel> listAiModels(LlmConfig c) {
        var models = OllamaModels.builder()
                .baseUrl(c.getUrl())
                .timeout(ProviderRequestSupport.MODEL_TIMEOUT)
                .build()
                .availableModels()
                .content();
        var result = new ArrayList<AiModel>(models.size());
        for (OllamaModel m : models) {
            result.add(AiModel.builder().id(m.getName()).name(m.getModel()).build());
        }
        Collections.sort(result, (a, b) -> a.getId().compareTo(b.getId()));
        return result;
    }

    @Override
    public ExtraBodyMode extraBodyMode() {
        return ExtraBodyMode.NONE;
    }

    @Override
    public ThinkSupport thinkSupport() {
        return new ThinkSupport.Boolean();
    }
}
