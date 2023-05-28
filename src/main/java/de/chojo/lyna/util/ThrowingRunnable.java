package de.chojo.lyna.util;

import java.util.concurrent.Callable;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}
