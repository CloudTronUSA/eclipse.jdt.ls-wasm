/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.teavm.classlib.java.util.concurrent;

import org.teavm.classlib.java.lang.TRunnable;
import org.teavm.classlib.java.lang.TObject;

public interface TExecutorService extends TExecutor {
    <T extends TObject> TFuture<T> submit(TCallable<T> task);

    TFuture<?> submit(TRunnable task);

    void shutdown();

    @Override
    default void execute(TRunnable command) {
        command.run();
    }
}
