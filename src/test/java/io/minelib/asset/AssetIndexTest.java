package io.minelib.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssetIndexTest {

    @Test
    void assetObjectRelativePathIsCorrect() {
        // Construct an AssetObject via Gson-like reflective access or through a helper
        // Since AssetIndex.AssetObject has no public constructor, we use Gson to deserialise.
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = "{\"objects\":{\"icons/icon_16x16.png\":{\"hash\":\"bdf48ef6b5d0d23bbb02e17d04865216179f510a\",\"size\":3665}}}";
        AssetIndex index = gson.fromJson(json, AssetIndex.class);

        assertNotNull(index.getObjects());
        assertEquals(1, index.getObjects().size());

        AssetIndex.AssetObject obj = index.getObjects().get("icons/icon_16x16.png");
        assertNotNull(obj);
        assertEquals("bdf48ef6b5d0d23bbb02e17d04865216179f510a", obj.getHash());
        assertEquals(3665L, obj.getSize());
        // Relative path must be <first-2-chars>/<full-40-char-hash>
        assertEquals("bd/bdf48ef6b5d0d23bbb02e17d04865216179f510a", obj.getRelativePath());
    }

    @Test
    void emptyObjectsMapDoesNotThrow() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        AssetIndex index = gson.fromJson("{\"objects\":{}}", AssetIndex.class);
        assertNotNull(index.getObjects());
        assertTrue(index.getObjects().isEmpty());
    }
}
