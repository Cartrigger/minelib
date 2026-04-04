package io.minelib.launch;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A platform-independent handle to a running Minecraft instance.
 *
 * <p>On desktop, the game runs as an OS child process and a {@code GameProcess} is obtained
 * via {@link #ofProcess(Process)}. On Android, the game runs in-process on a thread (as
 * PojavLauncher does), and the handle is obtained via {@link #ofThread(Thread)}.
 *
 * <p>This abstraction lets calling code manage the game lifecycle uniformly regardless of
 * the underlying execution model.
 */
public abstract class GameProcess {

    GameProcess() {}

    /** Returns whether the game instance is still running. */
    public abstract boolean isAlive();

    /**
     * Waits for the game to exit.
     *
     * @return the exit code, or {@code 0} on platforms that do not provide one
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public abstract int waitFor() throws InterruptedException;

    /**
     * Requests that the game process or thread be stopped.
     *
     * <p>On desktop this sends {@code SIGTERM}; on Android it interrupts the game thread.
     * Neither approach guarantees immediate termination.
     */
    public abstract void destroy();

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Wraps a desktop OS {@link Process} as a {@link GameProcess}.
     *
     * @param process the child process returned by {@link ProcessBuilder#start()}
     * @return a {@link GameProcess} backed by the given OS process
     */
    public static GameProcess ofProcess(Process process) {
        if (process == null) throw new NullPointerException("process must not be null");
        return new ProcessGameProcess(process);
    }

    /**
     * Wraps an Android in-process game {@link Thread} as a {@link GameProcess}.
     *
     * <p>Use this factory when running Minecraft in-process on Android via reflection,
     * as PojavLauncher does.
     *
     * @param thread the thread executing the game's {@code main} method
     * @return a {@link GameProcess} backed by the given thread
     */
    public static GameProcess ofThread(Thread thread) {
        if (thread == null) throw new NullPointerException("thread must not be null");
        return new ThreadGameProcess(thread);
    }

    // -------------------------------------------------------------------------
    // Implementations
    // -------------------------------------------------------------------------

    private static final class ProcessGameProcess extends GameProcess {

        private final Process process;

        ProcessGameProcess(Process process) {
            this.process = process;
        }

        @Override
        public boolean isAlive() {
            return process.isAlive();
        }

        @Override
        public int waitFor() throws InterruptedException {
            return process.waitFor();
        }

        @Override
        public void destroy() {
            process.destroy();
        }
    }

    private static final class ThreadGameProcess extends GameProcess {

        private final Thread thread;
        private final AtomicInteger exitCode = new AtomicInteger(0);

        ThreadGameProcess(Thread thread) {
            this.thread = thread;
        }

        @Override
        public boolean isAlive() {
            return thread.isAlive();
        }

        @Override
        public int waitFor() throws InterruptedException {
            thread.join();
            return exitCode.get();
        }

        @Override
        public void destroy() {
            thread.interrupt();
        }
    }
}
