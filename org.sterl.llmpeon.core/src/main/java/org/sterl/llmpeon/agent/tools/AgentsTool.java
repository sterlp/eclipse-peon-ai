package org.sterl.llmpeon.agent.tools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.model.CompactResult.Stats;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.CallStats;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.tools.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Paul WIP -- don't touch, don't commit ignore
 */
public class AgentsTool extends AbstractTool {
    public enum Status {
        RUNNING, COMPLETED
    }
    
    @RequiredArgsConstructor
    static class Task {
        final long id;
        final AiAgent agent;
        final String prompt;
        final CallStats start = CallStats.start();
        final CompletableFuture<Task> future = new CompletableFuture<>();

        volatile Status status = Status.RUNNING;
        volatile String result;
        volatile Throwable e;
        
        String agentName() {
            return this.agent.getName();
        }

        boolean isComplete() {
            return status == Status.COMPLETED || e != null;
        }

        boolean isFailed() {
            return e != null;
        }
        
        String status() {
            if (isFailed()) {
                return "task=" + id
                + " agent=" + agent.getName()
                + " status=FAILED" 
                + errorText();

            } else if (status == Status.COMPLETED) {
                return "task=" + id
                        + " agent=" + agent.getName()
                        + " status=COMPLETED" 
                        + "result:" + System.lineSeparator() 
                        + result;
            }
            return "task=" + id + " agent=" + agent.getName() + " status=" + status; 
        }
        
        String errorText() {
            if (e == null) return "";
            return " error: " + e.getClass().getSimpleName() + " " + e.getMessage()
                + " stack: " + System.lineSeparator() + stackTraceOf();
        }

        private String stackTraceOf() {
            if (e == null) return null;
            var sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            return sw.toString();
        }
    }
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * How many agents may run concurrently
     */
    @Setter
    private volatile int maxConcurrent = 0;
    private final AtomicInteger asyncRunning = new AtomicInteger(0);
    private final ConcurrentHashMap<Long, Task> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AiAgent> agents = new ConcurrentHashMap<>();

    public AgentsTool(List<AiAgent> agentList) {
        agentList.forEach(this::register);
    }

    public void register(AiAgent agent) {
        var previous = agents.putIfAbsent(agent.getName(), agent);
        if (previous != null) {
            throw new IllegalStateException(
                    "Agent already registered: " + agent.getName()
            );
        }
    }

    public Task spawn(String agentName, String prompt) {
        var agent = getAgent(agentName);
        ArgsUtil.requireNonBlank(prompt, "prompt");

        Task result;
        var runsAsync = this.maxConcurrent > this.asyncRunning.get();
        if (runsAsync) {
            monitor.onTool(agent.getName() + " start async: " + System.lineSeparator() + prompt);
            var slaveMonitor = runsAsync ? new AsyncMonitor(this.monitor) : monitor; 
            long taskId = ID_GENERATOR.incrementAndGet();
            result = new Task(taskId, agent, prompt);
            tasks.put(taskId, result);
            CompletableFuture.supplyAsync(
                    () -> dispatch(agent, prompt, new ArrayList<ContextItem>(), slaveMonitor),
                    virtualExecutor)
                .whenComplete((r, e) -> complete(result, r, e));
        } else {
            monitor.onTool(agent.getName() + " start: " + System.lineSeparator() + prompt);
            result = new Task(-1, agent, prompt);
            try {
                var agentResult = dispatch(agent, prompt, new ArrayList<ContextItem>(), monitor);
                complete(result, agentResult, null);
            } catch (Exception e) {
                complete(result, null, e);
            }
        }

        return result;
    }
    
    private static String dispatch(AiAgent target, String prompt,
            List<ContextItem> orders, AiMonitor monitor) {
        
        AiAgent slave = target;
        slave.setTurnContextSupplier(() -> orders);

        final var response = slave.call(prompt, monitor);
        
        final var answer = response != null
                ? response.aiMessage().text()
                : null;

        final var result = StringUtil.hasValue(answer)
                ? answer
                : target.getName() + " returned no result";
        return result;
    }

    private AiAgent getAgent(String agentName) {
        var agent = agents.get(agentName);
        if (agent == null) {
            String known = agents.keySet().stream()
                    .sorted()
                    .collect(Collectors.joining(", "));

            throw new IllegalArgumentException(
                    "Unknown agent: " + agentName + ". Known agents: " + known
            );
        }
        return agent;
    }

    private void complete(Task task, String result, Throwable throwable) {
        var target = task.agent;
        final var statsLine = contextUsed(target) + " " + task.start.suffix();
        
        task.status = Status.COMPLETED;
        task.result = result + System.lineSeparator() + target.getName() + statsLine;
        task.e = throwable;

        if (throwable != null) {
            task.e = throwable.getCause() != null
                    ? throwable.getCause()
                    : throwable;
            monitor.onProblem(target.getName() + " failed. " + task.e.getMessage() + " " + statsLine + " " + StringUtil.charsSuffix(result));
        } else {
            monitor.onTool(target.getName() + " done. " + statsLine + " " + StringUtil.charsSuffix(result));
        }

        task.future.complete(task);
    }


    @Tool("Read the current status and, if finished, the result of an async subagent task.")
    public String getTaskStatus(
            @P("Numeric task ID returned by spawn_agent") long taskId
    ) {
        var task = tasks.get(taskId);
        if (task == null) {
            return "Unknown task: " + taskId;
        }
        return task.status();
    }

    @Tool("Block until one async subagent task finishes, fails, is cancelled, or the timeout expires.")
    public String waitForTask(
            @P("Numeric task ID") Long taskId,
            @P(value = "Maximum wait time in seconds", required = false) Long timeoutSeconds
    ) {
        Task task = tasks.get(taskId);
        if (task == null) {
            return "Unknown task: " + taskId;
        }

        try {
            task.future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "task=" + taskId + " status=RUNNING timeout_after=" + timeoutSeconds + "s";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "task=" + taskId + " interrupted";
        } catch (ExecutionException e) {
            // Should normally not occur because complete() resolves the future.
            return "task=" + taskId + " execution_error=" + e.getMessage();
        }

        return getTaskStatus(taskId);
    }

    /*
    @Tool("List every available subagent, its skill description, and its task statistics.")
    public String listAgents() {
        if (agents.isEmpty()) {
            return "No agents registered.";
        }

        return agents.values().stream()
                .map(this::agentSummary)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String agentSummary(Agent agent) {
        List<TaskSnapshot> agentTasks = tasks.values().stream()
                .filter(task -> task.agent == agent)
                .map(Task::snapshot)
                .toList();

        long running = agentTasks.stream()
                .filter(task -> task.status() == Status.RUNNING)
                .count();
        long completed = agentTasks.stream()
                .filter(task -> task.status() == Status.COMPLETED)
                .count();
        long failed = agentTasks.stream()
                .filter(task -> task.status() == Status.FAILED)
                .count();

        return "agent=" + agent.name()
                + "\ndescription=" + agent.description()
                + "\nrunning=" + running
                + "\ncompleted=" + completed
                + "\nfailed=" + failed;
    }

    @Tool("Start an async subagent task. Returns immediately with a numeric task ID. Do not perform the delegated work yourself.")
    public String spawnAgent(
            @P("Exact name of the subagent") String agentName,
            @P("Complete, self-contained prompt for the subagent") String prompt
    ) {
        try {
            long taskId = spawn(agentName, prompt);
            return "task_id=" + taskId
                    + "\nagent=" + agentName
                    + "\nstatus=RUNNING"
                    + "\nUse get_task_status or wait_for_task with this task_id.";
        } catch (IllegalArgumentException e) {
            return "error=" + e.getMessage()
                    + "\navailable_agents=" + agents.keySet();
        }
    }

    public Map<Long, TaskSnapshot> snapshots() {
        return tasks.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().snapshot()
                ));
    }
*/
    
    @Tool("Wipe Agent back to blank — the next task is UNRELATED and the old state would only create drift.")
    public void subAgentClear(@P("Exact name of the subagent") String agentName) {
        var agent = getAgent(agentName);
        agent.getMemory().clear();
        reportAction(agent, "reset", monitor);
    }
    
    @Tool("Compact Agent — the SAME task continues but the history got long. Keeps the gist, frees context.")
    public void subAgentCompact(@P("Exact name of the subagent") String agentName) {
        // R-CT-8: no own line — the sub-agent's compact already sent the one end line via onTool
        getAgent(agentName).compact(monitor); // todo this should be async too ...
    }

    private void reportAction(AiAgent target, String action, AiMonitor monitor) {
        monitor.onTool(target.getName() + " " + action + ".");
    }

    private static String contextUsed(AiAgent agent) {
        var memory = agent.getMemory();
        return "Context: " + StringUtil.estimateAware(memory.isTokenEstimate(), String.valueOf(memory.getTotalTokenUsed()))
                + " token - " + agent.tokenContextUsedInPercent() + "% used.";
    }
}
