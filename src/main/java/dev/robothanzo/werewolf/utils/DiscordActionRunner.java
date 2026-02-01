package dev.robothanzo.werewolf.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.dv8tion.jda.api.requests.RestAction;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DiscordActionRunner {

    @Data
    @AllArgsConstructor
    public static class ActionTask {
        public RestAction<?> action;
        public String description;
        public Consumer<Object> onSuccess;

        public ActionTask(RestAction<?> action, String description) {
            this.action = action;
            this.description = description;
            this.onSuccess = null;
        }
    }

    /**
     * Executes a list of Discord actions with progress tracking and status logging.
     *
     * @param tasks            List of actions to perform
     * @param statusLogger     Consumer for status messages
     * @param progressCallback Consumer for progress percentage
     * @param startPercent     The percentage to start from for this batch of
     *                         actions
     * @param endPercent       The percentage to reach after all actions are done
     * @param timeoutSeconds   Maximum time to wait for all actions to complete
     * @throws Exception if wait is interrupted or timed out
     */
    public static void runActions(List<ActionTask> tasks, Consumer<String> statusLogger,
                                  Consumer<Integer> progressCallback, int startPercent,
                                  int endPercent, int timeoutSeconds) throws Exception {
        int total = tasks.size();
        if (total == 0) {
            if (progressCallback != null)
                progressCallback.accept(endPercent);
            return;
        }

        AtomicInteger completed = new AtomicInteger(0);
        CompletableFuture<Void> allDone = new CompletableFuture<>();
        int range = endPercent - startPercent;

        for (ActionTask task : tasks) {
            task.getAction().queue(success -> {
                if (statusLogger != null)
                    statusLogger.accept("  - [完成] " + task.getDescription());
                if (task.getOnSuccess() != null)
                    task.getOnSuccess().accept(success);
                handleTaskCompletion(completed, total, allDone, progressCallback, startPercent, range);
            }, error -> {
                if (statusLogger != null)
                    statusLogger.accept("  - [失敗] " + task.getDescription() + ": " + error.getMessage());
                handleTaskCompletion(completed, total, allDone, progressCallback, startPercent, range);
            });
        }

        try {
            allDone.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (statusLogger != null)
                statusLogger.accept("警告: 部分 Discord 變更操作逾時或中斷 (" + e.getMessage() + ")");
            throw e;
        }
    }

    private static void handleTaskCompletion(AtomicInteger completed, int total, CompletableFuture<Void> allDone,
                                             Consumer<Integer> progressCallback, int startPercent, int range) {
        int c = completed.incrementAndGet();
        if (progressCallback != null) {
            int currentProgress = startPercent + (int) ((c / (double) total) * range);
            progressCallback.accept(currentProgress);
        }
        if (c == total) {
            allDone.complete(null);
        }
    }
}
