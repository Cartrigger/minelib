package io.minelib.download;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages concurrent file downloads with optional SHA-1 verification.
 *
 * <p>All downloads are executed on a fixed-size thread pool whose concurrency is set at
 * construction time. Each file is first written to a temporary path and then atomically moved
 * to its final destination, so the library never leaves partially-written files behind.
 */
public final class DownloadManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadManager.class);
    private static final int BUFFER_SIZE = 8192;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;

    /**
     * Creates a new {@code DownloadManager} with the given concurrency limit.
     *
     * @param maxConcurrentDownloads the maximum number of simultaneous downloads
     */
    public DownloadManager(int maxConcurrentDownloads) {
        this.httpClient = new OkHttpClient();
        this.executor = Executors.newFixedThreadPool(maxConcurrentDownloads, r -> {
            Thread t = new Thread(r, "minelib-downloader");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Returns the underlying {@link OkHttpClient} for making ad-hoc HTTP requests
     * (e.g. querying metadata APIs in mod loader installers).
     */
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Downloads a single file, blocking the calling thread until it completes.
     *
     * @param task the download task
     * @throws IOException if the download or checksum verification fails
     */
    public void download(DownloadTask task) throws IOException {
        Path destination = task.getDestination();
        if (isAlreadyDownloaded(task)) {
            LOGGER.debug("Skipping already-downloaded file: {}", destination);
            return;
        }

        Files.createDirectories(destination.getParent());
        Path tempFile = Files.createTempFile(destination.getParent(), ".minelib-dl-", ".tmp");
        try {
            Request request = new Request.Builder()
                    .url(task.getUrl().toString())
                    .build();

            LOGGER.debug("Downloading {} -> {}", task.getUrl(), destination);
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Server returned HTTP " + response.code() + " for " + task.getUrl());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty response body for " + task.getUrl());
                }
                try (InputStream in = body.byteStream();
                     OutputStream out = Files.newOutputStream(tempFile)) {
                    in.transferTo(out);
                }
            }

            if (task.getSha1() != null) {
                verifySha1(tempFile, task.getSha1(), task.getUrl().toString());
            }

            // Prefer an atomic move; fall back on file-systems that don't support it
            // (e.g. Android's /data partition).
            try {
                Files.move(tempFile, destination,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.debug("Downloaded: {}", destination);
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw (e instanceof IOException ioe) ? ioe : new IOException("Download failed: " + task.getUrl(), e);
        }
    }

    /**
     * Submits a download task for asynchronous execution.
     *
     * @param task the download task
     * @return a future that completes when the download finishes, or completes exceptionally on error
     */
    public CompletableFuture<Void> downloadAsync(DownloadTask task) {
        return CompletableFuture.runAsync(() -> {
            try {
                download(task);
            } catch (IOException e) {
                throw new RuntimeException("Download failed: " + task.getUrl(), e);
            }
        }, executor);
    }

    /**
     * Submits multiple download tasks and waits for all of them to complete.
     *
     * @param tasks the list of tasks to download
     * @throws IOException if any download fails
     */
    public void downloadAll(List<DownloadTask> tasks) throws IOException {
        List<CompletableFuture<Void>> futures = new ArrayList<>(tasks.size());
        for (DownloadTask task : tasks) {
            futures.add(downloadAsync(task));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException("One or more downloads failed", cause);
        }
    }

    /**
     * Checks whether the destination file already exists and (if a SHA-1 is provided) whether its
     * checksum matches. If both conditions are met the file does not need to be re-downloaded.
     */
    private boolean isAlreadyDownloaded(DownloadTask task) {
        Path dest = task.getDestination();
        if (!Files.exists(dest)) {
            return false;
        }
        if (task.getSha1() == null) {
            return true;
        }
        try {
            String actual = sha1Hex(dest);
            return actual.equalsIgnoreCase(task.getSha1());
        } catch (IOException | NoSuchAlgorithmException e) {
            LOGGER.warn("Could not verify checksum for {}, will re-download", dest, e);
            return false;
        }
    }

    private void verifySha1(Path file, String expected, String urlHint)
            throws IOException, NoSuchAlgorithmException {
        String actual = sha1Hex(file);
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IOException(
                    "SHA-1 mismatch for " + urlHint + ": expected " + expected + " but got " + actual);
        }
    }

    private String sha1Hex(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] buf = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
