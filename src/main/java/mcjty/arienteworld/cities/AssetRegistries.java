package mcjty.arienteworld.cities;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.config.Config;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AssetRegistries {

    public static final AbstractAssetRegistry<BuildingPart> PARTS = new AbstractAssetRegistry<>();
    public static final AbstractAssetRegistry<Palette> PALETTES = new AbstractAssetRegistry<>();
    public static final AbstractAssetRegistry<CityPlan> CITYPLANS = new AbstractAssetRegistry<>();

    public static void reset() {
        PARTS.reset();
        PALETTES.reset();
        CITYPLANS.reset();
    }

    public static void load(File file) {
        try(FileInputStream in = new FileInputStream(file)) {
            load(in, file.getName());
        } catch (FileNotFoundException e) {
            // Not an error
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void initialize() {
        reset();
        for (String asset : Config.ASSETS) {
            if (asset.startsWith("$")) {
                load(FMLPaths.CONFIGDIR.get().resolve(asset.substring(1)).toFile());
            } else {
                InputStream stream = ArienteWorld.class.getResourceAsStream(asset);
                if (stream == null) {
                    throw new IllegalStateException("Missing city asset: " + asset);
                }
                load(stream, asset);
            }
        }
        validate();
    }

    public static void validate() {
        for (CityPlan plan : CITYPLANS.getIterable()) {
            Palette palette = PALETTES.get(plan.getPalette());
            if (palette == null) {
                throw new IllegalStateException("Missing palette for " + plan.getName());
            }
            for (PartPalette parts : plan.getPartPalette().values()) {
                for (String name : parts.getPalette()) {
                    BuildingPart part = PARTS.get(name);
                    if (part == null || part.getXSize() > 16 || part.getZSize() > 16) {
                        throw new IllegalStateException("Invalid city part: " + name);
                    }
                    for (int y = 0; y < part.getSliceCount(); y++) {
                        for (int z = 0; z < part.getZSize(); z++) {
                            for (int x = 0; x < part.getXSize(); x++) {
                                if (!palette.getPalette().containsKey(part.getC(x, y, z))) {
                                    throw new IllegalStateException("Missing palette entry in " + name + " at " + x + "," + y + "," + z);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void load(InputStream inputstream, String filename) {
        try(BufferedReader br = new BufferedReader(new InputStreamReader(inputstream, StandardCharsets.UTF_8))) {
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(br);
            for (JsonElement entry : element.getAsJsonArray()) {
                JsonObject object = entry.getAsJsonObject();
                String type = object.get("type").getAsString();
                if ("palette".equals(type)) {
                    PALETTES.register(new Palette(object));
                } else if ("part".equals(type)) {
                    PARTS.register(new BuildingPart(object));
                } else if ("plan".equals(type)) {
                    CITYPLANS.register(new CityPlan(object));
                } else {
                    throw new RuntimeException("Unknown type '" + type + " in " + filename + "'!");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
