package io.minelib.asset;

import java.util.Map;

/**
 * Represents a Minecraft asset index file, which maps virtual asset paths to their
 * hash-addressed counterparts in the {@code assets/objects} directory.
 *
 * <p>The JSON structure looks like:
 * <pre>
 * {
 *   "objects": {
 *     "icons/icon_16x16.png": { "hash": "bdf48ef6b5d0d23bbb02e17d04865216179f510a", "size": 3665 },
 *     ...
 *   }
 * }
 * </pre>
 */
public final class AssetIndex {

    private Map<String, AssetObject> objects;

    /** Returns the full map of virtual path → {@link AssetObject} entries. */
    public Map<String, AssetObject> getObjects() {
        return objects;
    }

    /**
     * A single asset entry containing its SHA-1 hash and size.
     *
     * <p>The asset is stored at
     * {@code assets/objects/<first-two-chars-of-hash>/<full-hash>}
     * and downloaded from
     * {@code https://resources.download.minecraft.net/<first-two-chars-of-hash>/<full-hash>}.
     */
    public static final class AssetObject {

        private String hash;
        private long size;

        /** Returns the SHA-1 hash (40 hex chars) of this asset. */
        public String getHash() {
            return hash;
        }

        /** Returns the file size in bytes. */
        public long getSize() {
            return size;
        }

        /**
         * Returns the relative storage path within {@code assets/objects}, e.g.
         * {@code "bdf48e/bdf48ef6b5d0d23bbb02e17d04865216179f510a"}.
         */
        public String getRelativePath() {
            return hash.substring(0, 2) + "/" + hash;
        }
    }
}
