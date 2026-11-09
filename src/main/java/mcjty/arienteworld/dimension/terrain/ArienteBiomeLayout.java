package mcjty.arienteworld.dimension.terrain;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.world.level.ChunkPos;

/** Equally weighted biome selection and seven seeded zoom layers, sampled in quart coordinates. */
public class ArienteBiomeLayout {

    private final Layer layer;

    public ArienteBiomeLayout(long seed) {
        Layer current = new Layer(seed, 1, null);
        for (int i = 0; i < 7; i++) {
            current = new Layer(seed, 1000 + i, current);
        }
        layer = current;
    }

    public BiomeProfile get(int quartX, int quartZ) {
        return BiomeProfile.VALUES[layer.get(quartX, quartZ)];
    }

    public boolean isCityChunk(int chunkX, int chunkZ) {
        int count = 0;
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                if (get(chunkX * 4 + x, chunkZ * 4 + z) == BiomeProfile.CITY) {
                    count++;
                }
            }
        }
        return count > 8;
    }

    private static long mix(long value, long salt) {
        return value * (value * 6364136223846793005L + 1442695040888963407L) + salt;
    }

    private static class Layer {

        private final long seed;
        private final Layer parent;
        private final Cache<Long, Integer> cache = CacheBuilder.newBuilder().maximumSize(16384).build();

        private Layer(long worldSeed, long salt, Layer parent) {
            long base = salt;
            for (int i = 0; i < 3; i++) {
                base = mix(base, salt);
            }
            long value = worldSeed;
            for (int i = 0; i < 3; i++) {
                value = mix(value, base);
            }
            seed = value;
            this.parent = parent;
        }

        private int get(int x, int z) {
            long key = ChunkPos.pack(x, z);
            Integer cached = cache.getIfPresent(key);
            if (cached != null) {
                return cached;
            }
            int value = sample(x, z);
            cache.put(key, value);
            return value;
        }

        private int sample(int x, int z) {
            if (parent == null) {
                return new LayerRandom(seed, x, z).next(BiomeProfile.VALUES.length);
            }
            int px = x >> 1;
            int pz = z >> 1;
            int a = parent.get(px, pz);
            if ((x & 1) == 0 && (z & 1) == 0) {
                return a;
            }
            int b = parent.get(px + 1, pz);
            int c = parent.get(px, pz + 1);
            int d = parent.get(px + 1, pz + 1);
            LayerRandom random = new LayerRandom(seed, px * 2, pz * 2);
            int south = random.next(2) == 0 ? a : c;
            int east = random.next(2) == 0 ? a : b;
            if ((x & 1) == 0) {
                return south;
            }
            if ((z & 1) == 0) {
                return east;
            }
            if (b == c && c == d) {
                return b;
            }
            if (a == b && a == c) {
                return a;
            }
            if (a == b && a == d) {
                return a;
            }
            if (a == c && a == d) {
                return a;
            }
            if (a == b && c != d) {
                return a;
            }
            if (a == c && b != d) {
                return a;
            }
            if (a == d && b != c) {
                return a;
            }
            if (b == c && a != d) {
                return b;
            }
            if (b == d && a != c) {
                return b;
            }
            if (c == d && a != b) {
                return c;
            }
            return new int[]{a, b, c, d}[random.next(4)];
        }
    }

    private static class LayerRandom {

        private final long seed;
        private long state;

        private LayerRandom(long seed, long x, long z) {
            this.seed = seed;
            state = mix(mix(mix(mix(seed, x), z), x), z);
        }

        private int next(int bound) {
            int value = (int) Math.floorMod(state >> 24, bound);
            state = mix(state, seed);
            return value;
        }
    }
}
