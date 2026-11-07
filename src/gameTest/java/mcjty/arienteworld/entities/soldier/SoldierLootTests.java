package mcjty.arienteworld.entities.soldier;

import mcjty.ariente.items.KeyCardItem;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ai.CityAI;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class SoldierLootTests {

    @GameTest(template = "empty")
    public static void invalidCityKeysSkipOnlyTheTaggedReward(GameTestHelper helper) {
        for (String key : Arrays.asList(null, "", "  ", "valid-city-key")) {
            CityAI city = new CityAI(helper.getLevel(), new ChunkPos(8, 8)) {

                @Override
                public String getKeyId() {
                    return key;
                }

                @Override
                public String getForcefieldId() {
                    return key;
                }
            };
            for (float roll : new float[]{0, .399f, .4f, .799f}) {
                ItemStack reward = SoldierEntity.createCityKeyCard(city, roll);
                if (key == null || key.isBlank()) {
                    helper.assertTrue(reward.isEmpty(), "An invalid tagged reward must be skipped without producing a blank card");
                } else {
                    helper.assertTrue(!reward.isEmpty() && KeyCardItem.getSecurityTags(reward).contains(key),
                            "Valid door and forcefield cards retain their security tag");
                }
            }
            ItemStack blank = SoldierEntity.createCityKeyCard(city, .8f);
            helper.assertTrue(!blank.isEmpty() && KeyCardItem.getSecurityTags(blank).isEmpty(),
                    "The independent blank-card roll is preserved");
        }
        helper.succeed();
    }
}
