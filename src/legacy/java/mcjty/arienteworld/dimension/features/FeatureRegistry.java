package mcjty.arienteworld.dimension.features;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class FeatureRegistry {

    private static final Map<String, IFeature> features = new HashMap<>();

    public static void init() {
        addFeature(new SpheresFeature());
        addFeature(new SpikesFeature());
        addFeature(new BubbleFeature());
        addFeature(new GlowBubbleFeature());
    }

    private static void addFeature(IFeature feature) {
        features.put(feature.getId(), feature);
    }

    public static IFeature getFeature(String id) {
        return features.get(id);
    }

    public static Collection<IFeature> getFeatures() {
        return features.values();
    }
}
