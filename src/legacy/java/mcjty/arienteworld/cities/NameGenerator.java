package mcjty.arienteworld.cities;

import java.util.Random;

public class NameGenerator {

    private static final String[] STARTS = {
            "Ar", "Le", "De", "Ora", "Zio", "In", "To", "Be", "Ane", "Dio", "Za", "Ah", "Feo",
            "Li", "Ra", "Wi", "Um", "Do", "Roh", "Ada", "Ida", "Weh", "Cha", "Chi", "Teo",
            "Lhi", "Lhe", "Whe", "Tho", "Ido", "Zhe", "Cov"
    };
    private static final String[] MIDS = {
            "ba", "za", "nio", "ga", "ge", "fo", "tao", "loa", "re", "wo", "thi", "tho", "tha",
            "ra", "me", "meh", "ka", "ji", "jo", "ri", "fi", "fhi", "rhe", "ghe", "gha", "fho",
            "fhe", "fha", "fe"
    };
    private static final String[] ENDS = {
            "tam", "dol", "ni", "ra", "loi", "tol", "pas", "nah", "feh", "fa", "ba", "ton", "lo",
            "rah", "poh", "gih", "roi", "la", "reh", "rih", "hach", "mar", "po", "fe", "ru", "rul",
            "tai", "fai", "lai"
    };

    public static String randomName(Random random) {
        String s = STARTS[random.nextInt(STARTS.length)];
        if (random.nextFloat() < .5f) {
            s += MIDS[random.nextInt(MIDS.length)];
        }
        s += ENDS[random.nextInt(ENDS.length)];
        return s;
    }

    public static void main(String[] args) {
        System.out.println("STARTS = " + STARTS.length);
        System.out.println("MIDS = " + MIDS.length);
        System.out.println("ENDS = " + ENDS.length);
        System.out.println("STARTS.length*ENDS.length + STARTS.length*MIDS.length*ENDS.length = " + (STARTS.length * ENDS.length + STARTS.length * MIDS.length * ENDS.length));
        Random random = new Random(System.currentTimeMillis());
        for (int i = 0 ; i < 50 ; i++) {
            if (i % 10 == 0) {
                System.out.println("");
            }
            System.out.print(randomName(random) + " ");
        }
        System.out.println("");
    }
}
