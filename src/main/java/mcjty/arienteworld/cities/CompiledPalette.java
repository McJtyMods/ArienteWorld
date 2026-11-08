package mcjty.arienteworld.cities;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import mcjty.lib.varia.Logging;
import net.minecraft.world.level.block.state.BlockState;

/**
 * More efficient representation of a palette useful for a single chunk
 */
public class CompiledPalette {

    private final Map<PaletteIndex, BlockState> palette = new HashMap<>();

    private static final Map<String, CompiledPalette> compiledPaletteMap = new ConcurrentHashMap<>();

    public static CompiledPalette getCompiledPalette(String name) {
        return compiledPaletteMap.computeIfAbsent(name, key -> new CompiledPalette(AssetRegistries.PALETTES.get(key)));
    }

    public static CompiledPalette getNewCompiledPalette(String name) {
        compiledPaletteMap.remove(name);
        return getCompiledPalette(name);
    }

    public CompiledPalette(CompiledPalette other, Palette... palettes) {
        this.palette.putAll(other.palette);
        addPalettes(palettes);
    }

    public CompiledPalette(Palette... palettes) {
        addPalettes(palettes);
    }

    public void addPalettes(Palette[] palettes) {
        // First add the straight palette entries
        for (Palette p : palettes) {
            for (Map.Entry<PaletteIndex, BlockState> entry : p.getPalette().entrySet()) {
                BlockState value = entry.getValue();
                palette.put(entry.getKey(), value);
            }
        }
    }

    public Set<PaletteIndex> getCharacters() {
        return palette.keySet();
    }

    public BlockState getStraight(PaletteIndex c) {
        try {
            return palette.get(c);
        } catch (Exception e) {
            Logging.logError("Internal error", e);
            return null;
        }
    }

    public BlockState get(PaletteIndex c) {
        return palette.get(c);
    }
}
