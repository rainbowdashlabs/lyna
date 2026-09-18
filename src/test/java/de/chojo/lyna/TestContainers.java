/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna;

import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Starts test containers one at a time across every Gradle test fork.
 *
 * <p>The test tasks fork one JVM per two CPU cores, and each fork owns its own containers, so on a
 * many-core machine several of them request a host port within the same moment. Rootless Docker
 * occasionally hands two of them the same port and fails the start with an internal server error;
 * because every fork then retries on the same schedule, {@code withStartupAttempts} exhausts its
 * retries and the whole test class dies in {@code initializationError}.
 *
 * <p>Holding an inter-process file lock for the duration of the start serialises only the container
 * creation. Tests still run in parallel afterwards, so two starts never overlap.
 *
 * <p>Serialising alone is not enough, because rootless Docker picks a free host port and binds it in
 * two steps and any outbound socket on the machine can take it in between. The reaper container that
 * Testcontainers starts once per fork lost that race far more often than the databases did, so it is
 * disabled for the test tasks and the shutdown hook registered here stops the container instead.
 */
public final class TestContainers {
    private static final Path LOCK_FILE =
            Path.of(System.getProperty("java.io.tmpdir"), "lyna-testcontainer-start.lock");
    private static final Object JVM_LOCK = new Object();

    private TestContainers() {}

    /**
     * Starts the container unless it is already running, letting no other fork start one meanwhile.
     *
     * @param container the container to start
     */
    public static void startExclusively(GenericContainer<?> container) {
        if (container.isRunning()) return;
        synchronized (JVM_LOCK) {
            if (container.isRunning()) return;
            try (FileChannel channel =
                            FileChannel.open(LOCK_FILE, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                container.start();
            } catch (IOException e) {
                container.start();
            }
            Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
        }
    }
}
