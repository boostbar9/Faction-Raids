package com.devfarinsky.siegeoverhaul.items;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class SpellbookArtworkTest {
    private byte[] resource(String name) throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream,name);return stream.readAllBytes();
        }
    }
    @Test void nativeModelsResolveDistinctUnmodifiedCreditedTextures() throws Exception {
        var manifest=JsonParser.parseString(new String(resource("META-INF/credits/weekly-dot/assets.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("Hungry (Hungry22)",manifest.get("author").getAsString());
        String notice=new String(resource("META-INF/credits/weekly-dot/AUTHOR-NOTICE.txt"),StandardCharsets.UTF_8);
        assertTrue(notice.contains("BY-NC-SA"));assertTrue(notice.contains("Copying, distribution"));
        assertFalse(notice.contains("user:"));assertFalse(notice.contains("id:"));
        var hashes=new HashSet<String>();var items=new HashSet<String>();
        for(var entry:manifest.getAsJsonArray("assets")) {
            var asset=entry.getAsJsonObject();String item=asset.get("item").getAsString();items.add(item);
            var model=JsonParser.parseString(new String(resource("assets/siegeoverhaul/models/item/"+item+".json"),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("minecraft:item/generated",model.get("parent").getAsString());
            String texture=model.getAsJsonObject("textures").get("layer0").getAsString();
            String path="assets/"+texture.replace(":","/textures/")+".png";
            assertEquals(asset.get("texture").getAsString(),path);
            byte[] bytes=resource(path);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(asset.get("sha256").getAsString(),hash);assertTrue(hashes.add(hash));
            var image=ImageIO.read(new ByteArrayInputStream(bytes));assertNotNull(image);
            assertEquals(16,image.getWidth());assertEquals(16,image.getHeight());assertTrue(image.getColorModel().hasAlpha());
        }
        assertEquals(java.util.Set.of("tempest_spellbook","inferno_spellbook","undertow_spellbook"),items);
    }
}
