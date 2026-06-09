/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.teavm.classlib.javax.tools;

import java.io.InputStream;
import java.io.OutputStream;

public interface TTool {
    default int run(InputStream in, OutputStream out, OutputStream err, String... arguments) {
        return 1;
    }
}
