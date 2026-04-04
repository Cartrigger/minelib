package io.minelib.download;

import java.net.URI;
import java.nio.file.Path;

/**
 * Represents a single file download with an optional expected checksum.
 *
 * <p>Both SHA-1 (Mojang) and SHA-256 (Adoptium) checksums are supported.
 * When both are set, SHA-256 is preferred because it is stronger.
 */
public final class DownloadTask {

    private final URI url;
    private final Path destination;
    private final String sha1;
    private final String sha256;
    private final long size;

    private DownloadTask(Builder builder) {
        this.url = builder.url;
        this.destination = builder.destination;
        this.sha1 = builder.sha1;
        this.sha256 = builder.sha256;
        this.size = builder.size;
    }

    /** Returns the source URL. */
    public URI getUrl() {
        return url;
    }

    /** Returns the local path to which the file will be saved. */
    public Path getDestination() {
        return destination;
    }

    /**
     * Returns the expected SHA-1 hex digest of the downloaded file, or {@code null} if
     * not set.  Used by Mojang's version/library metadata.
     */
    public String getSha1() {
        return sha1;
    }

    /**
     * Returns the expected SHA-256 hex digest of the downloaded file, or {@code null} if
     * not set.  Used by the Adoptium JRE download API.
     */
    public String getSha256() {
        return sha256;
    }

    /**
     * Returns the expected file size in bytes, or {@code -1} if the size is not known.
     */
    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "DownloadTask{url=" + url + ", destination=" + destination + '}';
    }

    /** Creates a new builder for {@link DownloadTask}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link DownloadTask}. */
    public static final class Builder {

        private URI url;
        private Path destination;
        private String sha1;
        private String sha256;
        private long size = -1;

        private Builder() {}

        /**
         * Sets the download URL.
         *
         * @param url the URL to download from
         * @return this builder
         */
        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        /**
         * Sets the download URL from a string.
         *
         * @param url the URL string to download from
         * @return this builder
         */
        public Builder url(String url) {
            this.url = URI.create(url);
            return this;
        }

        /**
         * Sets the local destination path.
         *
         * @param destination the path to save the file to
         * @return this builder
         */
        public Builder destination(Path destination) {
            this.destination = destination;
            return this;
        }

        /**
         * Sets the expected SHA-1 checksum (hex string) for verification.
         * Used for Mojang library and asset downloads.
         *
         * @param sha1 the expected SHA-1 digest, or {@code null} to skip SHA-1 verification
         * @return this builder
         */
        public Builder sha1(String sha1) {
            this.sha1 = sha1;
            return this;
        }

        /**
         * Sets the expected SHA-256 checksum (hex string) for verification.
         * Used for Adoptium JRE archive downloads.
         * When both SHA-1 and SHA-256 are set, SHA-256 is preferred by
         * {@link DownloadManager}.
         *
         * @param sha256 the expected SHA-256 digest, or {@code null} to skip SHA-256 verification
         * @return this builder
         */
        public Builder sha256(String sha256) {
            this.sha256 = sha256;
            return this;
        }

        /**
         * Sets the expected file size in bytes.
         *
         * @param size the expected file size, or {@code -1} if unknown
         * @return this builder
         */
        public Builder size(long size) {
            this.size = size;
            return this;
        }

        /** Builds the {@link DownloadTask}. */
        public DownloadTask build() {
            if (url == null) throw new IllegalStateException("url must be set");
            if (destination == null) throw new IllegalStateException("destination must be set");
            return new DownloadTask(this);
        }
    }
}
