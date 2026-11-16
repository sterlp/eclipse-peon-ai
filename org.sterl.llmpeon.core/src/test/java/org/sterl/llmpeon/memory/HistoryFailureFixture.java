package org.sterl.llmpeon.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import dev.langchain4j.data.message.ChatMessage;

/**
 * A real history store whose armed write goes against a path below a regular file — the genuine
 * {@link IOException} of the real store, no chmod, no synthetic throw. Every write is recorded with the
 * list it was given and the memory's RAM list at that moment.
 */
public class HistoryFailureFixture extends FileAgentHistoryStore {

    public record Attempt(String type, List<ChatMessage> written, List<ChatMessage> ramAtAttempt) {}

    public final List<Attempt> attempts = Collections.synchronizedList(new ArrayList<>());
    private final FileAgentHistoryStore blocked;
    private volatile Supplier<List<ChatMessage>> ram = List::of;
    private volatile boolean armed;

    public HistoryFailureFixture(Path historyFile, Path blockerDir) throws IOException {
        super(historyFile);
        var blocker = Files.writeString(blockerDir.resolve("blocker"), "i am a file");
        blocked = new FileAgentHistoryStore(blocker.resolve("history.jsonl"));
    }

    public void observeRam(Supplier<List<ChatMessage>> ram) {
        this.ram = ram;
    }

    /** From now on the next write fails; earlier recorded attempts are dropped. */
    public void arm() {
        attempts.clear();
        armed = true;
    }

    @Override
    public void append(List<ChatMessage> messages) throws IOException {
        attempts.add(new Attempt("APPEND", List.copyOf(messages), ram.get()));
        if (armed) blocked.append(messages);
        else super.append(messages);
    }

    @Override
    public void persist(List<ChatMessage> messages) throws IOException {
        attempts.add(new Attempt("PERSIST", List.copyOf(messages), ram.get()));
        if (armed) blocked.persist(messages);
        else super.persist(messages);
    }
}
