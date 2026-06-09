/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.teavm.classlib.java.util.concurrent;

public final class TExecutors {
    private TExecutors() {
    }

    public static TExecutorService newSingleThreadExecutor() {
        return new TThreadPoolExecutor(1, 1, 0, TTimeUnit.MILLISECONDS, null, null);
    }

    public static TExecutorService newCachedThreadPool(TThreadFactory threadFactory) {
        return new TThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TTimeUnit.SECONDS, null, threadFactory);
    }
}
