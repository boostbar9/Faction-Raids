package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.BooleanSupplier;

/** Source-backed plan cards: the actual blueprint block models stay visible at supported GUI sizes. */
final class BuildingPlanButton extends Button {
    private static final ItemStack STONE = new ItemStack(Items.COBBLESTONE);
    private static final ItemStack WOOD = new ItemStack(Items.OAK_PLANKS);
    private final DefenseBlueprint.Kind kind;
    private final BooleanSupplier selected;
    private final long stone, wood;
    private final BuildingPlanThumbnail thumbnail;

    BuildingPlanButton(DefenseBlueprint.Kind kind, OnPress press, int x, int y, int width,
                       int height, BooleanSupplier selected) {
        super(x, y, width, height, Component.literal(kind.label), press, DEFAULT_NARRATION);
        this.kind = kind;
        this.selected = selected;
        var blocks = DefenseBlueprint.create(kind, BlockPos.ZERO, Direction.SOUTH).blocks();
        stone = blocks.values().stream().filter("minecraft:cobblestone"::equals).count();
        wood = blocks.size() - stone;
        thumbnail = new BuildingPlanThumbnail(blocks);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int x = getX(), y = getY();
        boolean chosen = selected.getAsBoolean();
        CommandFrame.card(g, x, y, width, height,
                chosen ? CommandPalette.ACCENT_TEAL : CommandPalette.ACCENT_STEEL,
                isHoveredOrFocused());
        if (chosen) g.fill(x + 1, y + 2, x + 3, y + height - 2, CommandPalette.ACCENT_TEAL);
        draw(g, kind.label, x + 7, y + 3, width - 14,
                chosen ? CommandPalette.ACCENT_TEAL : CommandPalette.TEXT);
        if (showsThumbnail(width, height)) {
            boolean detailed = height >= 96;
            thumbnail.render(g, x + 7, y + 16, width - 14, height - (detailed ? 43 : 30));
            if (detailed) draw(g, kind.width + " x " + kind.depth + " · " + kind.height + " clear",
                    x + 7, y + height - 23, width - 14, CommandPalette.TEXT_MUTED);
            String price = kind.price + "e build";
            int priceWidth = Minecraft.getInstance().font.width(price);
            draw(g, "Free plan", x + 7, y + height - 10, width - priceWidth - 22, CommandPalette.TEXT_DIM);
            draw(g, price, x + width - priceWidth - 7, y + height - 10, priceWidth, CommandPalette.ACCENT_GOLD);
            return;
        }
        draw(g, height >= 52 ? "Free plan · " + kind.price + "e build" : kind.price + "e build", x + 7, y + height - 10,
                width - 14, CommandPalette.ACCENT_GOLD);
        if (height >= 52) draw(g, kind.width + " x " + kind.depth + " · " + kind.height + " clear",
                x + 7, y + 18, width - 14, CommandPalette.TEXT_MUTED);
        int materialsY = materialRowY(height);
        if (materialsY >= 0) {
            ItemIcons.draw(g, STONE, x + 8, y + materialsY);
            draw(g, Long.toString(stone), x + 28, y + materialsY + 4, width / 2 - 30, CommandPalette.TEXT);
            ItemIcons.draw(g, WOOD, x + width / 2, y + materialsY);
            draw(g, Long.toString(wood), x + width / 2 + 20, y + materialsY + 4, width / 2 - 27, CommandPalette.TEXT);
        }
        if (height >= 84) {
            draw(g, "Supplied blocks", x + 7, y + 57, width - 14, CommandPalette.TEXT_DIM);
        }
    }

    /** Compact 60–83px cards retain a real material row when the illustration would be too small. */
    static int materialRowY(int cardHeight) {
        return cardHeight < 60 ? -1 : cardHeight >= 84 ? 36 : cardHeight - 30;
    }

    static boolean showsThumbnail(int cardWidth, int cardHeight) { return cardWidth >= 100 && cardHeight >= 64; }

    private void draw(GuiGraphics g, String value, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        int available = Math.max(1, width);
        String shown = font.plainSubstrByWidth(value, available);
        if (!shown.equals(value) && available > font.width("…")) {
            shown = font.plainSubstrByWidth(value, available - font.width("…")) + "…";
        }
        g.drawString(font, shown, x, y, color, false);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        output.add(NarratedElementType.HINT, Component.literal(
                (selected.getAsBoolean() ? "Selected. " : "Select plan. ") + kind.description
                        + " " + kind.dimensions() + ". Free to collect; " + kind.price
                        + " faction Treasury emeralds on placement, plus " + stone
                        + " cobblestone and " + wood + " oak planks from Workers storage."));
    }
}
