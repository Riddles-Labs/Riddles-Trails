package com.riddleslabs.trails.quest;

import com.riddleslabs.api.scheduler.TaskHandle;

/** Handles cancellation/retirement before the scheduler has returned its handle. */
final class QuestTask implements AutoCloseable {
    private TaskHandle handle;
    private boolean closed;
    void bind(TaskHandle task) {
        boolean cancel;
        synchronized (this) {
            cancel = closed;
            if (!cancel) handle = task;
        }
        // Cancellation may invoke a retirement callback; never hold this lock across it.
        if (cancel) task.cancel();
    }
    @Override public void close() {
        TaskHandle task;
        synchronized (this) {
            closed = true;
            task = handle;
            handle = null;
        }
        if (task != null) task.cancel();
    }
}
