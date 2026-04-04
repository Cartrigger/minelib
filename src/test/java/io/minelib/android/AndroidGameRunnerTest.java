package io.minelib.android;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AndroidGameRunnerTest {

    @Test
    void parsedCommandExtractsSystemProperties() {
        List<String> command = List.of(
                "/java/bin/java",
                "-Dfoo=bar",
                "-Dbaz=qux",
                "-Xmx2048m",
                "com.example.Main",
                "--username", "Steve"
        );

        AndroidGameRunner.ParsedCommand parsed = AndroidGameRunner.ParsedCommand.from(command);
        assertEquals("bar", parsed.getSystemProperties().get("foo"));
        assertEquals("qux", parsed.getSystemProperties().get("baz"));
        // Memory flags should not appear in system properties
        assertNull(parsed.getSystemProperties().get("Xmx2048m"));
    }

    @Test
    void parsedCommandExtractsMainClass() {
        List<String> command = List.of(
                "/java/bin/java",
                "-Dfoo=bar",
                "net.minecraft.client.main.Main",
                "--version", "1.21"
        );

        AndroidGameRunner.ParsedCommand parsed = AndroidGameRunner.ParsedCommand.from(command);
        assertEquals("net.minecraft.client.main.Main", parsed.getMainClass());
    }

    @Test
    void parsedCommandExtractsGameArgs() {
        List<String> command = List.of(
                "/java/bin/java",
                "com.example.Main",
                "--username", "Steve",
                "--version", "1.21"
        );

        AndroidGameRunner.ParsedCommand parsed = AndroidGameRunner.ParsedCommand.from(command);
        assertEquals(List.of("--username", "Steve", "--version", "1.21"), parsed.getGameArgs());
    }

    @Test
    void parsedCommandExtractsClasspathFromCpFlag() {
        String sep = File.pathSeparator;
        String cp = "/path/to/a.jar" + sep + "/path/to/b.jar";
        List<String> command = List.of(
                "/java/bin/java",
                "-cp", cp,
                "com.example.Main"
        );

        AndroidGameRunner.ParsedCommand parsed = AndroidGameRunner.ParsedCommand.from(command);
        assertEquals(2, parsed.getClasspathEntries().size());
        assertEquals(Path.of("/path/to/a.jar"), parsed.getClasspathEntries().get(0));
        assertEquals(Path.of("/path/to/b.jar"), parsed.getClasspathEntries().get(1));
    }

    @Test
    void parsedCommandHandlesClasspathFlag() {
        String cp = "/path/to/a.jar";
        List<String> command = List.of(
                "/java/bin/java",
                "--classpath", cp,
                "com.example.Main"
        );

        AndroidGameRunner.ParsedCommand parsed = AndroidGameRunner.ParsedCommand.from(command);
        assertEquals(1, parsed.getClasspathEntries().size());
    }

    @Test
    void parsedCommandWithNoMainClassReturnsNull() {
        List<String> command = List.of(
                "/java/bin/java",
                "-Dfoo=bar"
        );

        AndroidGameRunner.ParsedCommand parsed = AndroidGameRunner.ParsedCommand.from(command);
        assertNull(parsed.getMainClass());
        assertTrue(parsed.getGameArgs().isEmpty());
    }
}
