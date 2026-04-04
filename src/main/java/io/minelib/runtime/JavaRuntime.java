package io.minelib.runtime;

import java.nio.file.Path;

/**
 * Represents a provisioned Java runtime that can be used to launch Minecraft.
 */
public final class JavaRuntime {

    private final int majorVersion;
    private final String vendor;
    private final Path javaHome;

    public JavaRuntime(int majorVersion, String vendor, Path javaHome) {
        this.majorVersion = majorVersion;
        this.vendor = vendor;
        this.javaHome = javaHome;
    }

    /** Returns the Java major version (e.g. {@code 17} or {@code 21}). */
    public int getMajorVersion() {
        return majorVersion;
    }

    /** Returns the JVM vendor name (e.g. {@code "Eclipse Adoptium"}). */
    public String getVendor() {
        return vendor;
    }

    /**
     * Returns the root directory of this Java installation.
     * The executable is typically at {@code <javaHome>/bin/java}.
     */
    public Path getJavaHome() {
        return javaHome;
    }

    /**
     * Returns the path to the {@code java} (or {@code java.exe} on Windows) executable.
     */
    public Path getJavaExecutable() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return javaHome.resolve("bin").resolve(isWindows ? "java.exe" : "java");
    }

    @Override
    public String toString() {
        return "JavaRuntime{majorVersion=" + majorVersion + ", vendor='" + vendor
                + "', javaHome=" + javaHome + '}';
    }
}
