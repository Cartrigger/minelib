package io.minelib.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaRuntimeTest {

    @Test
    void gettersReturnConstructorValues() {
        Path home = Path.of("/usr/lib/jvm/java-17");
        JavaRuntime runtime = new JavaRuntime(17, "Eclipse Temurin", home);

        assertEquals(17, runtime.getMajorVersion());
        assertEquals("Eclipse Temurin", runtime.getVendor());
        assertEquals(home, runtime.getJavaHome());
    }

    @Test
    void javaExecutableIsUnderBinDirectory() {
        Path home = Path.of("/usr/lib/jvm/java-17");
        JavaRuntime runtime = new JavaRuntime(17, "Eclipse Temurin", home);

        Path exec = runtime.getJavaExecutable();
        assertTrue(exec.startsWith(home.resolve("bin")),
                "Executable must be under <javaHome>/bin, got: " + exec);
    }

    @Test
    void toStringContainsMajorVersion() {
        JavaRuntime runtime = new JavaRuntime(21, "Eclipse Temurin", Path.of("/java/home"));
        assertTrue(runtime.toString().contains("21"));
    }
}
