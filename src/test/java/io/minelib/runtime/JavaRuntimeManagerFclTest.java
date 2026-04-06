package io.minelib.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Android/FCL JDK helper methods on {@link JavaRuntimeManager}.
 *
 * <p>These tests exercise the static utility methods that can be tested without I/O.
 */
class JavaRuntimeManagerFclTest {

    // -------------------------------------------------------------------------
    // selectFclVersion
    // -------------------------------------------------------------------------

    @Test
    void selectFclVersion17ForRequired17() throws IOException {
        assertEquals(17, JavaRuntimeManager.selectFclVersion(17));
    }

    @Test
    void selectFclVersion17ForRequiredBelow17() throws IOException {
        assertEquals(17, JavaRuntimeManager.selectFclVersion(8));
        assertEquals(17, JavaRuntimeManager.selectFclVersion(11));
        assertEquals(17, JavaRuntimeManager.selectFclVersion(16));
    }

    @Test
    void selectFclVersion25ForRequired21() throws IOException {
        assertEquals(25, JavaRuntimeManager.selectFclVersion(21));
    }

    @Test
    void selectFclVersion25ForRequiredBetween17And25() throws IOException {
        assertEquals(25, JavaRuntimeManager.selectFclVersion(18));
        assertEquals(25, JavaRuntimeManager.selectFclVersion(20));
        assertEquals(25, JavaRuntimeManager.selectFclVersion(22));
        assertEquals(25, JavaRuntimeManager.selectFclVersion(24));
    }

    @Test
    void selectFclVersionThrowsForRequiredAbove25() {
        assertThrows(IOException.class, () -> JavaRuntimeManager.selectFclVersion(26));
        assertThrows(IOException.class, () -> JavaRuntimeManager.selectFclVersion(99));
    }

    // -------------------------------------------------------------------------
    // detectFclArchSuffix
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "aarch64,    aarch64",
            "arm64,      aarch64",
            "arm,        aarch32",
            "armv7l,     aarch32",
            "armeabi,    aarch32",
            "x86_64,     amd64",
            "amd64,      amd64",
            "x86,        i386",
            "i386,       i386",
            "i686,       i386",
    })
    void detectFclArchSuffixMapsCorrectly(String osArch, String expected) {
        String previous = System.getProperty("os.arch");
        try {
            System.setProperty("os.arch", osArch);
            assertEquals(expected, JavaRuntimeManager.detectFclArchSuffix());
        } finally {
            if (previous != null) {
                System.setProperty("os.arch", previous);
            } else {
                System.clearProperty("os.arch");
            }
        }
    }

    @Test
    void detectFclArchSuffixDefaultsToAarch64ForUnknownArch() {
        String previous = System.getProperty("os.arch");
        try {
            System.setProperty("os.arch", "riscv64");
            assertEquals("aarch64", JavaRuntimeManager.detectFclArchSuffix());
        } finally {
            if (previous != null) {
                System.setProperty("os.arch", previous);
            } else {
                System.clearProperty("os.arch");
            }
        }
    }
}
