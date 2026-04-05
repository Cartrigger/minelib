package io.minelib.modloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NeoForgeInstallerTest {

    @Test
    void derivedNeoPrefixForTwoPartVersion() {
        assertEquals("21.", NeoForgeInstaller.deriveNeoPrefix("1.21"));
    }

    @Test
    void derivedNeoPrefixForThreePartVersion() {
        assertEquals("21.4.", NeoForgeInstaller.deriveNeoPrefix("1.21.4"));
    }

    @Test
    void derivedNeoPrefixForSnapshotLike() {
        assertEquals("20.1.", NeoForgeInstaller.deriveNeoPrefix("1.20.1"));
    }
}
