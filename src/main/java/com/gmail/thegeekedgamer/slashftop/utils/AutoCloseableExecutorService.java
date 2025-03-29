package com.gmail.thegeekedgamer.slashftop.utils;

import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

public class AutoCloseableExecutorService implements ExecutorService, AutoCloseable {
    private final ExecutorService delegate;

    public AutoCloseableExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public @NotNull List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> @NotNull Future<T> submit(@NotNull Callable<T> task) {
        return delegate.submit(task);
    }

    @Override
    public <T> @NotNull Future<T> submit(@NotNull Runnable task, T result) {
        return delegate.submit(task, result);
    }

    @Override
    public @NotNull Future<?> submit(@NotNull Runnable task) {
        return delegate.submit(task);
    }

    @Override
    public <T> @NotNull List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasks);
    }

    @Override
    public <T> @NotNull List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return delegate.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> @NotNull T invokeAny(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(@NotNull Runnable command) {
        delegate.execute(command);
    }

    @Override
    public void close() {
        shutdown();
        try {
            if (!awaitTermination(60, TimeUnit.SECONDS)) {
                Log.warn("ExecutorService did not terminate within 60 seconds, forcing shutdown...");
                shutdownNow();
            }
        } catch (InterruptedException e) {
            Log.error("Interrupted while waiting for ExecutorService to terminate: " + e.getMessage());
            shutdownNow();
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
    }
}