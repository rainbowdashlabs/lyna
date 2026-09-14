package de.chojo.lyna.util;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}
