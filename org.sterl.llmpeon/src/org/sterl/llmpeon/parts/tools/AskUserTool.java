package org.sterl.llmpeon.parts.tools;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Lets the LLM pause and ask the user a clarifying question. The tool method
 * blocks the LangChain4j background thread using a {@link CountDownLatch} until
 * the user submits an answer or the request is canceled.
 */
public class AskUserTool extends AbstractEclipseTool {
    public static final String CANCEL = "[canceled]";
    
    @FunctionalInterface
    public interface QuestionPresenter {
        void show(String question, List<String> answers, Consumer<String> onAnswer);
    }

    private final QuestionPresenter presenter;

    public AskUserTool(QuestionPresenter presenter) {
        this.presenter = presenter;
    }

    @Tool("""
          Use this when you need the user's response to be matched to this specific question.
          Other user messages may arrive in the meantime; this tool returns the matching response, not an unrelated queued message.
          If the user cancels (Stop button), the tool returns an error.
          """)
    public String askUser(
            @P(name = "question", description="""
                    The question shown to the user. Markdown is supported. Include enough
                    context for the user to understand what is being asked.
                    """) 
            String question,
            @P(name = "predefinedAnswers", description = """
                    Optional answer choices displayed as selectable radio buttons - plain text only (no Markdown).
                    If omitted, the user can provide a free-form text answer.
                    """, required = false) 
            List<String> predefinedAnswers) {

        var latch = new CountDownLatch(1);
        var answer = new AtomicReference<>(CANCEL);

        presenter.show(
                question,
                predefinedAnswers != null ? predefinedAnswers : List.of(),
                a -> { answer.set(a); latch.countDown(); });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (CANCEL.equals(answer.get())) {
            throw new CancellationException("Canceled question " + question);
        }
        return answer.get();
    }
}
