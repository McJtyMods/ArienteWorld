package mcjty.arienteworld.cities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AbstractAssetRegistry<T extends IAsset> {

    private final Map<String, T> assets = new LinkedHashMap<>();
    private final List<String> assetNames = new ArrayList<>();

    public void register(T building) {
        if (!assets.containsKey(building.getName())) {
            assetNames.add(building.getName());
        }
        assets.put(building.getName(), building);
    }

    public void put(String name, T newversion) {
        assets.put(name, newversion);
    }

    public T get(String name) {
        if (name == null) {
            return null;
        }
        return assets.get(name);
    }

    public T get(int i) {
        return assets.get(assetNames.get(i));
    }

    public Iterable<T> getIterable() {
        return assets.values();
    }

    public int getCount() {
        return assets.size();
    }

    public String getName(int i) {
        return assetNames.get(i);
    }

    public void reset() {
        assets.clear();
        assetNames.clear();
    }
}
