package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.items.OlympianWeaponSkins;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class OlympianWeaponPresentationTest extends MinecraftTestSupport {
    private static JsonObject model(String patron, String shape) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/siegeoverhaul/models/item/olympian", patron+"_"+shape+".json"))).getAsJsonObject();
    }
    private static Vector3f vector(JsonArray array) {
        return new Vector3f(array.get(0).getAsFloat(),array.get(1).getAsFloat(),array.get(2).getAsFloat());
    }
    @Test void everyInventoryModelFitsItsSlotUsingNativeItemTransforms() throws Exception {
        for (var patron : OlympianWeaponSkins.PATRONS) for (var shape : OlympianWeaponSkins.SHAPES) {
            var json=model(patron,shape);
            var parsed=BlockModel.fromString(json.toString());
            var pose=new PoseStack();
            parsed.getTransforms().getTransform(ItemDisplayContext.GUI).apply(false,pose);
            float minX=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,minY=Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
            for (var value:json.getAsJsonArray("elements")) {
                var element=value.getAsJsonObject();
                var from=vector(element.getAsJsonArray("from"));var to=vector(element.getAsJsonArray("to"));
                for(int corner=0;corner<8;corner++) {
                    var point=new Vector3f((corner&1)==0?from.x:to.x,(corner&2)==0?from.y:to.y,(corner&4)==0?from.z:to.z);
                    if(element.has("rotation")) {
                        var rotation=element.getAsJsonObject("rotation");
                        var origin=vector(rotation.getAsJsonArray("origin"));
                        float angle=(float)Math.toRadians(rotation.get("angle").getAsFloat());
                        var q=new Quaternionf();
                        switch(rotation.get("axis").getAsString()) {
                            case "x" -> q.rotateX(angle);case "y" -> q.rotateY(angle);case "z" -> q.rotateZ(angle);
                        }
                        point.sub(origin).rotate(q).add(origin);
                    }
                    point.sub(8,8,8).div(16).mulPosition(pose.last().pose());
                    assertTrue(Math.abs(point.x)<=7.001F/16 && Math.abs(point.y)<=7.001F/16,
                            patron+"_"+shape+" protrudes beyond its one-pixel slot margin: "+point);
                    minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);
                    minY=Math.min(minY,point.y);maxY=Math.max(maxY,point.y);
                }
            }
            // Static families are centered and use most of a slot; ranged variants share a union fit.
            if(!shape.startsWith("bow") && !shape.startsWith("crossbow")) {
                assertEquals(0,minX+maxX,0.0001);assertEquals(0,minY+maxY,0.0001);
                assertTrue(Math.max(maxX-minX,maxY-minY)>.7,patron+"_"+shape+" is too small to read");
            }
        }
    }
    @Test void animationStatesKeepTheSameFramingAndHandAlignment() throws Exception {
        for(var patron:OlympianWeaponSkins.PATRONS) for(var family:new String[]{"bow","crossbow"}) {
            var idle=model(patron,family).getAsJsonObject("display");
            for(var shape:OlympianWeaponSkins.SHAPES) if(shape.startsWith(family+"_")) {
                var display=model(patron,shape).getAsJsonObject("display");
                for(var context:new String[]{"gui","firstperson_righthand","firstperson_lefthand","thirdperson_righthand","thirdperson_lefthand"})
                    assertEquals(idle.get(context),display.get(context),patron+"_"+shape+" shifts during "+context);
            }
        }
    }
    @Test void geometryHasBoundedCostAndExplicitMaterialUvs() throws Exception {
        for(var patron:OlympianWeaponSkins.PATRONS) for(var shape:OlympianWeaponSkins.SHAPES) {
            var json=model(patron,shape);var elements=json.getAsJsonArray("elements");
            assertTrue(elements.size()<=24,"Keep individual weapons below 144 baked faces");
            assertEquals("front",json.get("gui_light").getAsString());
            for(var element:elements) for(var face:element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                var uv=face.getValue().getAsJsonObject().getAsJsonArray("uv");
                assertNotNull(uv,"Implicit UVs stretch building textures along long weapons");
                assertEquals(4,uv.size());
                assertTrue(uv.get(0).getAsFloat()<uv.get(2).getAsFloat());
                assertTrue(uv.get(1).getAsFloat()<uv.get(3).getAsFloat());
                for(var coordinate:uv)assertTrue(coordinate.getAsFloat()>=0 && coordinate.getAsFloat()<=16);
            }
        }
    }
    @Test void patronIdentityIsNotOnlyARecolor() throws Exception {
        for(var shape:new String[]{"blade","staff"}) {
            var silhouettes=new HashSet<String>();
            for(var patron:OlympianWeaponSkins.PATRONS) {
                var geometry=new JsonArray();
                for(var element:model(patron,shape).getAsJsonArray("elements")) {
                    var box=element.getAsJsonObject();var bounds=new JsonArray();
                    bounds.add(box.get("from"));bounds.add(box.get("to"));geometry.add(bounds);
                }
                assertTrue(silhouettes.add(geometry.toString()),patron+" repeats another deity's "+shape+" geometry");
            }
        }
    }
}
