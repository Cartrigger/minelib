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
    void buildsWithSha256Field() {
        DownloadTask task = DownloadTask.builder()
                .url("https://example.com/jre.tar.gz")
                .destination(Path.of("/tmp/jre.tar.gz"))
                .sha256("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
                .size(50_000_000L)
                .build();

        assertNull(task.getSha1());
        assertEquals("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
                task.getSha256());
        assertEquals(50_000_000L, task.getSize());
    }

    @Test
    void buildsWithBothSha1AndSha256() {
        DownloadTask task = DownloadTask.builder()
                .url("https://example.com/file.jar")
                .destination(Path.of("/tmp/file.jar"))
                .sha1("aabbccdd")
                .sha256("eeff0011")
                .build();

        assertEquals("aabbccdd", task.getSha1());
        assertEquals("eeff0011", task.getSha256());
    }

    @Test
    void sha256IsNullByDefault() {
        DownloadTask task = DownloadTask.builder()
                .url("https://example.com/file.jar")
                .destination(Path.of("/tmp/file.jar"))
                .build();
        assertNull(task.getSha256());
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
