package mcjty.arienteworld.varia;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Tools {

    private static int g_seed = 123456789;

    public static int fastrand128() {
        g_seed = (214013 * g_seed + 2531011);
        return (g_seed >> 16) & 0x7F;
    }


    public static String getRandomFromList(Random random, List<Pair<Float, String>> list) {
        if (list.isEmpty()) {
            return null;
        }
        List<Pair<Float, String>> elements = new ArrayList<>();
        float totalweight = 0;
        for (Pair<Float, String> pair : list) {
            elements.add(pair);
            totalweight += pair.getKey();
        }
        float r = random.nextFloat() * totalweight;
        for (Pair<Float, String> pair : elements) {
            r -= pair.getKey();
            if (r <= 0) {
                return pair.getRight();
            }
        }
        return null;
    }

    public static int calcIndex(int x, int y, int z) {
        return (x << 12) | (z << 8) + y;
    }
}
