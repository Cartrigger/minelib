package io.minelib.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTest {

    @Test
    void isAndroidReturnsFalseOnDesktop() {
        // android.os.Build is never on the standard JVM classpath
        assertFalse(Platform.isAndroid());
    }

    @Test
    void exactlyOneOfWindowsMacLinuxIsTrue() {
        boolean win = Platform.isWindows();
        boolean mac = Platform.isMac();
        boolean linux = Platform.isLinux();
        // Exactly one must be true
        int count = (win ? 1 : 0) + (mac ? 1 : 0) + (linux ? 1 : 0);
        assertEquals(1, count, "Exactly one of isWindows/isMac/isLinux must be true");
    }

    @Test
    void minecraftOsNameIsOneOfKnownValues() {
        String name = Platform.minecraftOsName();
        assertTrue(
                name.equals("windows") || name.equals("osx") || name.equals("linux"),
                "minecraftOsName() returned unexpected value: " + name);
    }

    @Test
    void minecraftOsNameMatchesIndividualChecks() {
        String name = Platform.minecraftOsName();
        if (Platform.isWindows()) assertEquals("windows", name);
        else if (Platform.isMac())    assertEquals("osx", name);
        else                           assertEquals("linux", name);
    }
}
