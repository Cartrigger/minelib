package io.minelib.launch;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class GameProcessTest {

    @Test
    void ofThreadIsAliveWhileThreadRuns() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            started.countDown();
            try { release.await(); } catch (InterruptedException ignored) {}
        });
        t.start();
        started.await(); // Wait until thread has actually started

        GameProcess gp = GameProcess.ofThread(t);
        assertTrue(gp.isAlive());

        release.countDown(); // Let the thread exit
        gp.waitFor();
        assertFalse(gp.isAlive());
    }

    @Test
    void ofThreadWaitForReturnsZero() throws InterruptedException {
        Thread t = new Thread(() -> {});
        t.start();
        GameProcess gp = GameProcess.ofThread(t);
        assertEquals(0, gp.waitFor());
    }

    @Test
    void ofThreadDestroyInterruptsThread() throws InterruptedException {
        Thread t = new Thread(() -> {
            try { Thread.sleep(60_000); } catch (InterruptedException ignored) {}
        });
        t.start();

        GameProcess gp = GameProcess.ofThread(t);
        gp.destroy();
        t.join(2_000); // Give thread 2 s to respond to interrupt
        assertFalse(t.isAlive(), "Thread should have exited after destroy()");
    }

    @Test
    void ofProcessNullThrows() {
        assertThrows(NullPointerException.class, () -> GameProcess.ofProcess(null));
    }

    @Test
    void ofThreadNullThrows() {
        assertThrows(NullPointerException.class, () -> GameProcess.ofThread(null));
    }
}
