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

    @Tool("Ask the user a clarifying question, one at a time. "
        + "Always include your recommended answer in the question. "
        + "Returns \"[canceled]\" if the user dismissed the question.")
    public String askUser(
            @P(name = "question", description = "the question to present to the user") 
            String question,
            @P(name = "predefinedAnswers", description = "optional list of answer choices shown as radio buttons", required = false) 
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
