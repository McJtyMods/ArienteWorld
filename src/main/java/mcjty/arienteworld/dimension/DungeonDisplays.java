package mcjty.arienteworld.dimension;

import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

import net.minecraft.core.HolderLookup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mcjty.ariente.display.DisplayAppearance;
import mcjty.ariente.display.DisplayBackground;
import mcjty.ariente.display.DisplayBaseTile;
import mcjty.ariente.display.DisplayModuleItem;
import mcjty.ariente.display.InstalledModule;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.PaletteIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Decorative consoles, authored entirely inside the current part and chunk. */
public final class DungeonDisplays {

    private DungeonDisplays() {
    }

    public static void decorate(BuildingPart part, Palette palette, long seed, int chunkX, int chunkZ) {
        if (!palette.getPalette().containsKey(new PaletteIndex('@', 'n'))
                || !palette.getPalette().containsKey(new PaletteIndex('@', 's'))) {
            return;
        }
        // Keep display choices independent of the existing interior and loot random streams.
        Random random = new Random(seed ^ chunkX * 341873128712L ^ chunkZ * 132897987541L
                ^ part.getName().hashCode() ^ 0x416c69656eL);
        String name = part.getName();
        if (name.equals("c5_e1") || name.equals("c5_w1")) {
            for (int deck = 0; deck <= 20; deck += 10) {
                for (int x : new int[]{5, 10}) {
                    for (int z : new int[]{4, 11}) {
                        // Only the low control desks have an empty space above this support.
                        if (part.getC(x, deck + 1, z).equals(new PaletteIndex('!', '2')) && random.nextBoolean()) {
                            add(part, palette, new BlockPos(x, deck + 2, z), z == 4 ? Direction.SOUTH : Direction.NORTH, random, false);
                        }
                    }
                }
            }
        } else if (name.startsWith("c3_1_")) {
            for (int x : new int[]{4, 11}) {
                for (int z : new int[]{3, 10}) {
                    for (int y = 2; y < part.getSliceCount() - 1; y++) {
                        // The red strip identifies the newly generated low console banks.
                        if (part.getC(x, y, z).equals(new PaletteIndex('!', '4'))) {
                            add(part, palette, new BlockPos(x, y, z), Direction.NORTH, random, false);
                        }
                    }
                }
            }
            wallConsoles(part, palette, random, 2);
        } else if ((name.startsWith("building") || name.startsWith("c4_1_")) && random.nextInt(3) == 0) {
            wallConsoles(part, palette, random, name.startsWith("building") ? 1 : 2);
        }
    }

    private static void wallConsoles(BuildingPart part, Palette palette, Random random, int count) {
        List<ConsolePosition> candidates = new ArrayList<>();
        for (int y = 2; y < part.getSliceCount() - 2; y++) {
            for (int x = 2; x < part.getXSize() - 2; x++) {
                for (int z = 2; z < part.getZSize() - 2; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!air(part, palette, pos) || !air(part, palette, pos.below()) || !air(part, palette, pos.above())
                            || !structural(part, palette, pos.below(2))) {
                        continue;
                    }
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        BlockPos front = pos.relative(facing);
                        if (palette.getPalette().containsKey(displayMaterial(facing))
                                && structural(part, palette, pos.relative(facing.getOpposite()))
                                && structural(part, palette, front.below(2))
                                && air(part, palette, front.below()) && air(part, palette, front) && air(part, palette, front.above())) {
                            candidates.add(new ConsolePosition(pos, facing));
                        }
                    }
                }
            }
        }
        Collections.shuffle(candidates, random);
        List<BlockPos> placed = new ArrayList<>();
        for (ConsolePosition candidate : candidates) {
            if (placed.stream().noneMatch(pos -> pos.distSqr(candidate.pos()) < 16)
                    && add(part, palette, candidate.pos(), candidate.facing(), random, true)) {
                placed.add(candidate.pos());
                if (placed.size() == count) {
                    return;
                }
            }
        }
    }

    private static boolean structural(BuildingPart part, Palette palette, BlockPos pos) {
        BlockState state = state(part, palette, pos);
        if (state == null || CityFeature.hasBlockEntity(state) || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return false;
        }
        String name = state.getBlock().getDescriptionId();
        return name.startsWith("block.ariente.marble") || name.startsWith("block.ariente.blacktech") || name.equals("block.ariente.pattern");
    }

    private static PaletteIndex displayMaterial(Direction facing) {
        return new PaletteIndex('@', facing.getName().charAt(0));
    }

    private static boolean add(BuildingPart part, Palette palette, BlockPos pos, Direction facing, Random random, boolean pedestal) {
        if (UndergroundAccess.isReserved(part, pos.getX(), pos.getZ())) {
            return false;
        }
        BlockState support = state(part, palette, pos.below(pedestal ? 2 : 1));
        if (support == null || CityFeature.hasBlockEntity(support)
                || !support.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                || !air(part, palette, pos.above()) || pedestal && !air(part, palette, pos.below())
                || part.getTeInfo().containsKey(pos) || pedestal && part.getTeInfo().containsKey(pos.below())) {
            return false;
        }
        BlockState previous = state(part, palette, pos);
        if (previous == null || CityFeature.hasBlockEntity(previous)
                || !previous.isAir() && !posInsideConsole(part, pos)) {
            return false;
        }
        for (int y = -1; y <= 1; y++) {
            if (!air(part, palette, pos.relative(facing).offset(0, y, 0))) {
                return false;
            }
        }
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < 4; line++) {
            if (line > 0) {
                text.append('\n');
            }
            for (int column = 0; column < 4; column++) {
                text.append("0123456789ABCDEF".charAt(random.nextInt(16)));
            }
        }
        int index = pos.getZ() * part.getXSize() + pos.getX();
        if (pedestal) {
            part.getSlice(pos.getY() - 1).getSlice().set(index, new PaletteIndex('!', '1'));
        }
        part.getSlice(pos.getY()).getSlice().set(index, displayMaterial(facing));
        int[] colors = {0x83eaff, 0x9fffc2, 0xffc278};
        part.getTeInfo().put(pos, Map.of("AlienText", text.toString(), "AlienColor", colors[random.nextInt(colors.length)]));
        return true;
    }

    private static boolean posInsideConsole(BuildingPart part, BlockPos pos) {
        return part.getName().startsWith("c3_1_") && part.getC(pos.getX(), pos.getY(), pos.getZ()).equals(new PaletteIndex('!', '4'));
    }

    private static BlockState state(BuildingPart part, Palette palette, BlockPos pos) {
        if (pos.getX() < 0 || pos.getX() >= part.getXSize() || pos.getZ() < 0 || pos.getZ() >= part.getZSize()
                || pos.getY() < 0 || pos.getY() >= part.getSliceCount()) {
            return null;
        }
        return palette.getPalette().get(part.getC(pos.getX(), pos.getY(), pos.getZ()));
    }

    private static boolean air(BuildingPart part, Palette palette, BlockPos pos) {
        BlockState state = state(part, palette, pos);
        return state != null && state.isAir();
    }

    public static void configure(DisplayBaseTile display, Map<String, Object> data, HolderLookup.Provider provider) {
        ItemStack stack = new ItemStack(Registration.DISPLAY_ALIEN_TEXT.get());
        DisplayModuleItem.editConfig(stack, config -> {
            config.putString("Text", (String) data.get("AlienText"));
            config.putInt("Color", (Integer) data.getOrDefault("AlienColor", 0x83eaff));
        });
        CompoundTag tag = new CompoundTag();
        tag.putInt("Height", 1);
        new DisplayAppearance(DisplayBackground.TRANSPARENT, 0x18445c, 75, true).write(tag);
        ListTag modules = new ListTag();
        modules.add(new InstalledModule(stack, 4, 4).save(false, provider));
        tag.put("Modules", modules);
        display.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, provider, tag));
        display.setChanged();
    }

    private record ConsolePosition(BlockPos pos, Direction facing) {
    }
}
