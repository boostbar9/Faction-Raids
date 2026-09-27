package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import top.theillusivec4.curios.api.SlotContext;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CuriosCompatTest extends MinecraftTestSupport {
    @Test void equippedModifiersUseNativeSlotIdsAndCosmeticSlotsHaveNoPower() {
        var attributes=List.of(Attributes.ARMOR_TOUGHNESS,Attributes.ARMOR,Attributes.MAX_HEALTH);
        for(int i=0;i<attributes.size();i++) {
            var attribute=attributes.get(i);var relic=new CuriosCompat.Relic(attribute,i==0?1:2);
            var id=UUID.randomUUID();var context=new SlotContext("charm",null,0,false,true);
            var stack=new ItemStack(Items.BLAZE_POWDER);
            var modifiers=relic.getAttributeModifiers(context,id,stack);
            assertEquals(1,modifiers.size());var modifier=modifiers.get(attribute).iterator().next();
            assertEquals(id,modifier.getId());assertEquals(i==0?1:2,modifier.getAmount());
            assertEquals(AttributeModifier.Operation.ADDITION,modifier.getOperation());
            assertFalse(relic.canEquipFromUse(context,stack));assertEquals(1,stack.getCount());
            assertTrue(relic.getAttributeModifiers(new SlotContext("charm",null,0,true,true),id,stack).isEmpty());
            assertTrue(relic.getAttributeModifiers(context,id,ItemStack.EMPTY).isEmpty());
        }
    }
    @Test void charmDataMergesAndExposesOnlyOurThreeRelics() throws Exception {
        var root=Path.of("src/main/resources");
        var tag=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("data/curios/tags/items/charm.json"))).getAsJsonObject();
        assertFalse(tag.get("replace").getAsBoolean());assertEquals(3,tag.getAsJsonArray("values").size());
        for(var id:List.of("forge_ember","owl_seal","sun_laurel"))assertTrue(tag.toString().contains("siegeoverhaul:"+id));
        var slot=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("data/siegeoverhaul/curios/slots/charm.json"))).getAsJsonObject();
        assertEquals(1,slot.get("size").getAsInt());assertEquals("SET",slot.get("operation").getAsString());
        assertFalse(slot.has("replace"));
        var entities=Files.readString(root.resolve("data/siegeoverhaul/curios/entities/players.json"));
        assertTrue(entities.contains("minecraft:player"));assertTrue(entities.contains("charm"));
    }
}
