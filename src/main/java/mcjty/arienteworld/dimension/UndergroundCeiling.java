package mcjty.arienteworld.dimension;

import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.PaletteIndex;
import net.minecraft.world.level.ChunkPos;

/** Six-by-eight recessed panels, joined by blue ribs across the four-by-four-chunk ceiling. */
public final class UndergroundCeiling {

    private UndergroundCeiling() {
    }

    public static void decorate(BuildingPart part, Palette palette, int chunkX, int chunkZ) {
        for (char symbol : new char[]{'p', 'b', 'n', 's', 'w', 'e'}) {
            if (!palette.getPalette().containsKey(material(symbol))) {
                return;
            }
        }
        ChunkPos center = CityTools.getNearestDungeonCenter(chunkX, chunkZ);
        int offsetX = (chunkX - center.x + 2) * 16;
        int offsetZ = (chunkZ - center.z + 2) * 16;
        for (int z = 0; z < part.getZSize(); z++) {
            for (int x = 0; x < part.getXSize(); x++) {
                int panelX = Math.floorMod(offsetX + x, 7);
                int panelZ = Math.floorMod(offsetZ + z, 9);
                PaletteIndex block;
                if (panelX == 0 || panelZ == 0) {
                    block = new PaletteIndex('!', panelX == 0 && panelZ == 0 ? '3' : 'a');
                } else if ((panelX == 3 || panelX == 4) && panelZ >= 3 && panelZ <= 6) {
                    block = material('b');
                } else if ((panelX == 3 || panelX == 4) && (panelZ == 2 || panelZ == 7)) {
                    block = material(panelZ == 2 ? 'n' : 's');
                } else if ((panelX == 2 || panelX == 5) && panelZ >= 3 && panelZ <= 6) {
                    block = material(panelX == 2 ? 'w' : 'e');
                } else {
                    block = material('p');
                }
                part.getSlice(0).getSlice().set(z * part.getXSize() + x, block);
            }
        }
    }

    private static PaletteIndex material(char symbol) {
        return new PaletteIndex('~', symbol);
    }
}
