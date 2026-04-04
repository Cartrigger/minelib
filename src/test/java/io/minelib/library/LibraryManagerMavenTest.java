package io.minelib.library;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LibraryManagerMavenTest {

    @Test
    void mavenCoordinatesToPathNoClassifier() {
        String path = LibraryManager.mavenCoordinatesToPath(
                "net.fabricmc", "fabric-loader", "0.16.10", null);
        assertEquals("net/fabricmc/fabric-loader/0.16.10/fabric-loader-0.16.10.jar", path);
    }

    @Test
    void mavenCoordinatesToPathWithClassifier() {
        String path = LibraryManager.mavenCoordinatesToPath(
                "org.lwjgl", "lwjgl", "3.3.3", "natives-linux");
        assertEquals("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar", path);
    }

    @Test
    void mavenCoordinatesToPathGroupWithMultipleDots() {
        String path = LibraryManager.mavenCoordinatesToPath(
                "com.google.code.gson", "gson", "2.10.1", null);
        assertEquals("com/google/code/gson/gson/2.10.1/gson-2.10.1.jar", path);
    }
}
