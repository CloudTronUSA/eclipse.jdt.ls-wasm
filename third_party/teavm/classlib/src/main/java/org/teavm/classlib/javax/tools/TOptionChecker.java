/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.teavm.classlib.javax.tools;

public interface TOptionChecker {
    default int isSupportedOption(String option) {
        return -1;
    }
}
