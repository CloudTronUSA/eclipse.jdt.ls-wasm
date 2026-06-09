/*
 *  Copyright 2025 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.impl.nio;

import java.nio.Buffer;

public final class Buffers {
    private Buffers() {
    }

    public static void free(Buffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Can only free direct buffer");
        }
        releaseNative(buffer);
    }

    private static native void releaseNative(Buffer buffer);
}
