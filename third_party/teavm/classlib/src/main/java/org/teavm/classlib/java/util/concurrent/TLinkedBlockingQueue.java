/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.teavm.classlib.java.util.concurrent;

import java.util.Collection;

public class TLinkedBlockingQueue<E> extends TArrayBlockingQueue<E> {
    private static final int DEFAULT_CAPACITY = 1 << 20;

    public TLinkedBlockingQueue() {
        super(DEFAULT_CAPACITY);
    }

    public TLinkedBlockingQueue(int capacity) {
        super(capacity);
    }

    public TLinkedBlockingQueue(Collection<? extends E> collection) {
        super(Math.max(1, Math.max(collection.size(), DEFAULT_CAPACITY)), false, collection);
    }
}
