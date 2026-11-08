package mcjty.arienteworld.cities;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Translates the block-state notation in the original city assets. */
public class LegacyBlockStates {

    public static BlockState read(String description) {
        String[] split = description.split("@", 2);
        String id = split[0];
        Map<String, String> properties = new LinkedHashMap<>();
        if (split.length == 2) {
            if (split[1].equals("1") && id.equals("ariente:negarite_generator")) {
                properties.put("facing", "south");
            } else {
                for (String entry : split[1].split(",")) {
                    String[] pair = entry.split("=", 2);
                    if (pair.length != 2) {
                        throw new IllegalArgumentException("Invalid city block state: " + description);
                    }
                    properties.put(pair[0], pair[1]);
                }
            }
        }
        if (id.matches("ariente:(marble|marble_smooth|marble_pilar|marble_bricks|marble_slab|double_marble_slab)")) {
            String color = properties.getOrDefault("color", "gray");
            properties.remove("color");
            if (id.equals("ariente:double_marble_slab")) {
                id = "ariente:marble_slab";
                properties.put("type", "double");
            }
            id += "_" + color;
        } else if (id.equals("ariente:blacktech") || id.equals("ariente:marbletech")) {
            String type = properties.getOrDefault("type", id.endsWith(":blacktech") ? "dots" : "variation1");
            // These names predate even the final 1.12 palette. Retain the closest line texture.
            if (type.equals("lines0") || type.equals("lines2")) {
                type = "lines";
            }
            properties.remove("type");
            id += "_" + type;
        } else if (id.equals("minecraft:stained_glass") || id.equals("minecraft:stained_glass_pane")) {
            id = "minecraft:" + properties.remove("color") + "_" + id.substring("minecraft:".length());
        }
        id = switch (id) {
            case "minecraft:mob_spawner" -> "minecraft:spawner";
            case "ariente:bluelog" -> "arienteworld:bluelog";
            case "ariente:blueleaves" -> "arienteworld:blueleaves";
            case "arienteworld:platinumore" -> "ariente:platinumore";
            case "arienteworld:soldier_dummy" -> "arienteworld:solider_dummy";
            case "arienteworld:master_soldier_dummy" -> "arienteworld:master_solider_dummy";
            default -> id;
        };
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            throw new IllegalArgumentException("Unknown city block: " + description + " (" + key + ")");
        }
        Block block = BuiltInRegistries.BLOCK.get(key);
        BlockState state = block.defaultBlockState();
        if (block instanceof LeavesBlock) {
            // Leaves in authored city parts must survive independently of generated tree logs.
            state = state.setValue(LeavesBlock.PERSISTENT, true);
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                throw new IllegalArgumentException("Unknown property in city block: " + description);
            }
            state = withProperty(state, property, entry.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        T parsed = property.getValue(value).orElseThrow(() -> new IllegalArgumentException("Invalid " + property.getName() + "=" + value + " for " + state));
        return state.setValue(property, parsed);
    }

    public static String write(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (state.getValues().isEmpty()) {
            return id;
        }
        return id + "@" + state.getValues().entrySet().stream()
                .map(entry -> writeProperty(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(","));
    }

    private static <T extends Comparable<T>> String writeProperty(Property<T> property, Comparable<?> value) {
        return property.getName() + "=" + property.getName((T) value);
    }
}
