/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.teavm.classlib.java.util.concurrent;

import org.teavm.classlib.java.lang.TRunnable;
import org.teavm.classlib.java.lang.TThread;

public interface TThreadFactory {
    TThread newThread(TRunnable runnable);
}
