package mcjty.arienteworld.dimension;

import mcjty.arienteworld.cities.BuildingPart;

/** Keep generated equipment and consoles out of the authored stairs, entrance and door controls. */
public final class UndergroundAccess {

    private UndergroundAccess() {
    }

    public static boolean isReserved(BuildingPart part, int x, int z) {
        return switch (part.getName()) {
            case "c3_1_c" -> x >= 12 && z >= 6 && z <= 11;
            case "c3_1_e" -> x >= 11 && z >= 6;
            case "c3_1_f" -> z >= 6 && z <= 11;
            case "c3_1_i", "c3_1_m" -> x >= 10;
            default -> false;
        };
    }
}
