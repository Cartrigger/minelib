package io.minelib.download;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DownloadTaskTest {

    @Test
    void buildsWithAllFields() {
        DownloadTask task = DownloadTask.builder()
                .url("https://example.com/file.jar")
                .destination(Path.of("/tmp/file.jar"))
                .sha1("aabbccdd")
                .size(1024L)
                .build();

        assertEquals(URI.create("https://example.com/file.jar"), task.getUrl());
        assertEquals(Path.of("/tmp/file.jar"), task.getDestination());
        assertEquals("aabbccdd", task.getSha1());
        assertEquals(1024L, task.getSize());
    }

    @Test
    void buildsWithUriUrl() {
        DownloadTask task = DownloadTask.builder()
                .url(URI.create("https://example.com/file.jar"))
                .destination(Path.of("/tmp/file.jar"))
                .build();

        assertEquals(URI.create("https://example.com/file.jar"), task.getUrl());
        assertNull(task.getSha1());
        assertEquals(-1L, task.getSize());
    }

    @Test
    void throwsWhenUrlMissing() {
        assertThrows(IllegalStateException.class, () ->
                DownloadTask.builder()
                        .destination(Path.of("/tmp/file.jar"))
                        .build());
    }

    @Test
    void throwsWhenDestinationMissing() {
        assertThrows(IllegalStateException.class, () ->
                DownloadTask.builder()
                        .url("https://example.com/file.jar")
                        .build());
    }

    @Test
    void toStringContainsUrl() {
        DownloadTask task = DownloadTask.builder()
                .url("https://example.com/file.jar")
                .destination(Path.of("/tmp/file.jar"))
                .build();

        assertTrue(task.toString().contains("example.com/file.jar"));
    }
}
