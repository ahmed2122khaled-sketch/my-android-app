package com.socialnetwork.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Shared bounded background execution policy.
 *
 * The app must never create an unbounded number of threads when the user
 * scrolls, refreshes, uploads media, or receives a burst of work. Six workers
 * allow useful network concurrency on modern devices while the bounded queue
 * provides a hard client-side memory/back-pressure limit. Rejected work is
 * never executed on the caller/UI thread; callers can handle rejection explicitly.
 */
public final class AppExecutors {
    private static final int IO_THREADS = 6;
    private static final int IO_QUEUE_CAPACITY = 256;

    private final ThreadPoolExecutor io = new ThreadPoolExecutor(
            IO_THREADS,
            IO_THREADS,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(IO_QUEUE_CAPACITY),
            new ThreadPoolExecutor.AbortPolicy()
    );

    public ExecutorService io() {
        return io;
    }

    public void shutdown() {
        io.shutdownNow();
    }
}
