package org.sterl.llmpeon.agent;

import java.util.ArrayList;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class AiCompressorAgent implements AiAgent {

    private static final SystemMessage COMPRESS_SYSTEM = SystemMessage.systemMessage("""
            You are a conversation compressor. Compress the conversation into a concise briefing. Structure the briefing into two parts:
            1. WHAT:  <Feature Name>
            Contains a concise summary auf all instruction from the user. What should be achived and which requirements do we know?
            2. HOW: <Design or Plan>
            How does the soltion looks like? What has been done till now? Which files have been modifed and why? Which components do what?

            Preserve:
            - Key decisions and their rationale
            - Current task state and pending work
            - Important file paths and code references
            - Tool results that are still relevant

            Remove:
            - Duplicate information and redundant exchanges
            - Verbose tool outputs (keep only the essential result)
            - Superseded decisions (only keep the final decision)
            - Greetings, filler, and pleasantries

            Format as a structured summary the developer can continue working from.
            Be as short but keeping all essential context and information.
            """);
    
    private final ChatModel chatModel;

    public AiCompressorAgent(ChatModel chatModel) {
        super();
        this.chatModel = chatModel;
    }
    
    public ChatResponse call(ChatRequest request, AiMonitor monitor) {
        var messages = new ArrayList<ChatMessage>();
        messages.add(COMPRESS_SYSTEM);
        messages.addAll(request.messages());
        request = request.toBuilder()
            .messages(messages)
            .build();

        if (monitor != null) monitor.onAction("Compressing conversation");
        return chatModel.chat(request);
    }
}
