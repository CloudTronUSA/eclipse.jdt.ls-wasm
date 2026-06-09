/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.teavm.classlib.java.util.concurrent;

import org.teavm.classlib.java.lang.TException;
import org.teavm.classlib.java.lang.TObject;

public class TThreadPoolExecutor implements TExecutorService {
    private boolean shutdown;

    public TThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TTimeUnit unit,
            TBlockingQueue<?> workQueue, TThreadFactory threadFactory) {
    }

    public void allowCoreThreadTimeOut(boolean value) {
    }

    @Override
    public <T extends TObject> TFuture<T> submit(TCallable<T> task) {
        try {
            return new CompletedFuture<>(task.call(), null);
        } catch (TException e) {
            return new CompletedFuture<>(null, e);
        }
    }

    @Override
    public TFuture<?> submit(org.teavm.classlib.java.lang.TRunnable task) {
        task.run();
        return new CompletedFuture<>(null, null);
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    public boolean isShutdown() {
        return shutdown;
    }

    private static final class CompletedFuture<T extends TObject> implements TFuture<T> {
        private final T result;
        private final TException error;

        CompletedFuture(T result, TException error) {
            this.result = result;
            this.error = error;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() throws TInterruptedException, TExecutionException {
            if (error != null) {
                throw new TExecutionException(error);
            }
            return result;
        }
    }
}
