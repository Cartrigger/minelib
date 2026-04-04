package io.minelib.library;

/**
 * Represents a resolved library artifact that has been classified as applicable for the
 * current operating system.
 */
public final class LibraryInfo {

    private final String name;
    private final String path;
    private final String sha1;
    private final long size;
    private final String url;
    private final boolean isNative;

    private LibraryInfo(Builder builder) {
        this.name = builder.name;
        this.path = builder.path;
        this.sha1 = builder.sha1;
        this.size = builder.size;
        this.url = builder.url;
        this.isNative = builder.isNative;
    }

    /** Returns the Maven-style coordinates (e.g. {@code "com.mojang:authlib:6.0.54"}). */
    public String getName() {
        return name;
    }

    /** Returns the relative path within the {@code libraries} directory. */
    public String getPath() {
        return path;
    }

    /** Returns the expected SHA-1 hex digest, or {@code null} if unknown. */
    public String getSha1() {
        return sha1;
    }

    /** Returns the file size in bytes, or {@code -1} if unknown. */
    public long getSize() {
        return size;
    }

    /** Returns the download URL. */
    public String getUrl() {
        return url;
    }

    /**
     * Returns {@code true} if this library contains OS-native binaries (e.g. LWJGL
     * natives) that must be extracted before launching the game.
     */
    public boolean isNative() {
        return isNative;
    }

    @Override
    public String toString() {
        return "LibraryInfo{name='" + name + "', native=" + isNative + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private String path;
        private String sha1;
        private long size = -1;
        private String url;
        private boolean isNative;

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder sha1(String sha1) { this.sha1 = sha1; return this; }
        public Builder size(long size) { this.size = size; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder isNative(boolean isNative) { this.isNative = isNative; return this; }

        public LibraryInfo build() {
            if (url == null || url.isBlank()) throw new IllegalStateException("url must be set");
            if (path == null || path.isBlank()) throw new IllegalStateException("path must be set");
            return new LibraryInfo(this);
        }
    }
}
