/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.teavm.classlib.java.io;

public class TLineNumberReader extends TBufferedReader {
    private int lineNumber;

    public TLineNumberReader(TReader in) {
        super(in);
    }

    public TLineNumberReader(TReader in, int size) {
        super(in, size);
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    @Override
    public String readLine() throws java.io.IOException {
        String line = super.readLine();
        if (line != null) {
            lineNumber++;
        }
        return line;
    }
}
