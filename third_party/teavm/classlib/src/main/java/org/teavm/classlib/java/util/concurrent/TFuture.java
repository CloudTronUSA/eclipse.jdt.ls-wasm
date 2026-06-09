/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.teavm.classlib.java.util.concurrent;

import org.teavm.classlib.java.lang.TObject;

public interface TFuture<V extends TObject> {
    boolean cancel(boolean mayInterruptIfRunning);

    boolean isCancelled();

    boolean isDone();

    V get() throws TInterruptedException, TExecutionException;
}
