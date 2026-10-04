package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.nativecompat.ProtectedBuildArea;
import com.devfarinsky.siegeoverhaul.nativecompat.ProtectedConstructionActions;
import com.talhanation.workers.client.gui.BuildAreaScreen;
import com.talhanation.workers.client.gui.structureRenderer.StructurePreviewWidget;
import com.talhanation.workers.client.gui.widgets.DisplayTextItemScrollDropDownMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Compact inspection using native Workers preview/material widgets and authenticated owner controls. */
public final class ProtectedConstructionScreen extends BuildAreaScreen {
    private ProtectedInspectionLayout.Layout layout;
    private Button projection;
    private int materialPage;
    private boolean previewFits = true;

    public ProtectedConstructionScreen(ProtectedBuildArea area, Player player) { super(area, player); }

    @Override public void setButtons() {
        // BuildAreaScreen.init already loads the actual accepted native NBT and
        // materials. Its fixed desktop layout is intentionally not constructed.
        clearWidgets();
        structureOptions = null; scanNameEditBox = null;
        freeAreaCheckBox = null; alwaysShowProjectionCheckBox = null;
        structurePreview = null; requiredItemsDropDownMenu = null;
        layout = ProtectedInspectionLayout.create(width, height, requiredItems.size());
        if (layout.content()) {
            var box = layout.preview();
            structurePreview = new StructurePreviewWidget(box.x(), box.y(), box.width(), box.height(),
                    buildArea.getWidthSize(), buildArea.getDepthSize());
            if (structure != null) structurePreview.setStructure(structure, structureNBT);
            addRenderableWidget(structurePreview);
            frameNativePreview(box);
            createMaterials();
        }
        projection = add(layout.projection(), projectionLabel(), ignored ->
                RaidNetwork.protectedConstructionAction(buildArea.getUUID(), buildArea.getAlwaysShowProjection()
                        ? ProtectedConstructionActions.HIDE : ProtectedConstructionActions.SHOW));
        projection.setTooltip(Tooltip.create(Component.literal("Show the native projection always, or only while focused.")));
        add(layout.cancel(), Component.literal("Cancel job"), ignored -> confirmCancel());
        add(layout.close(), Component.literal("Close"), ignored -> onClose());
    }

    private void frameNativePreview(ProtectedInspectionLayout.Rect box) {
        previewFits = true;
        if (structure == null || structure.isEmpty()) return;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (var block : structure) {
            var pos = block.relativePos();
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1); maxY = Math.max(maxY, pos.getY() + 1); maxZ = Math.max(maxZ, pos.getZ() + 1);
        }
        var frame = ProtectedPreviewFraming.initial(box.width(), box.height(), buildArea.getWidthSize(),
                buildArea.getDepthSize(), new ProtectedPreviewFraming.Bounds(minX, minY, minZ, maxX, maxY, maxZ));
        double x = box.x() + box.width() / 2.0, y = box.y() + box.height() / 2.0;
        // Public native inputs establish the camera; no rendering copy or private-field changes.
        structurePreview.setFocused(true);
        structurePreview.mouseScrolled(x, y, frame.zoom() - 3.5);
        structurePreview.setFocused(false);
        if (structurePreview.mouseClicked(x, y, 1)) {
            structurePreview.onGlobalMouseDragged(x + frame.dragX(), y + frame.dragY(), 1, frame.dragX(), frame.dragY());
            structurePreview.mouseReleased(x + frame.dragX(), y + frame.dragY(), 1);
        }
        previewFits = frame.fits();
    }

    private void createMaterials() {
        int capacity = layout.materialCapacity();
        int pages = Math.max(1, (requiredItems.size() + capacity - 1) / capacity);
        materialPage = Math.floorMod(materialPage, pages);
        int from = Math.min(requiredItems.size(), materialPage * capacity);
        List<ItemStack> visible = List.copyOf(requiredItems.subList(from, Math.min(requiredItems.size(), from + capacity)));
        var box = layout.materials();
        requiredItemsDropDownMenu = new KeyboardMaterials(box.x(), box.y(), box.width(), box.height(), visible);
        requiredItemsDropDownMenu.setBgFillSelected(0xFF535B67);
        requiredItemsDropDownMenu.setCanSelectItem(false);
        requiredItemsDropDownMenu.setResetCount(false);
        addRenderableWidget(requiredItemsDropDownMenu);
        if (pages > 1) add(layout.materialsPage(), Component.literal("Materials " + (materialPage + 1) + "/" + pages),
                ignored -> { materialPage++; setButtons(); });
    }

    private Button add(ProtectedInspectionLayout.Rect bounds, Component label, Button.OnPress action) {
        return addRenderableWidget(Button.builder(label, action)
                .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build());
    }

    @Override public void resetScan() { /* The accepted native plan is immutable. */ }
    @Override public void onAreaMoved() { /* Physical marker and native origin are sealed. */ }
    @Override public void tick() {
        super.tick();
        if (projection != null) projection.setMessage(projectionLabel());
        if (buildArea.isRemoved()) onClose();
    }
    private Component projectionLabel() {
        boolean always = buildArea.getAlwaysShowProjection();
        boolean compact = layout != null && layout.projection().width() < 112;
        return Component.literal(compact ? always ? "Always shown" : "Focus only" : "Projection: " + (always ? "Always" : "Focus"));
    }
    private void confirmCancel() {
        if (minecraft == null) return;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                RaidNetwork.protectedConstructionAction(buildArea.getUUID(), ProtectedConstructionActions.CANCEL);
                onClose();
            } else minecraft.setScreen(this);
        }, Component.literal("Cancel this construction job?"),
                Component.literal("Placed blocks remain. Supplied materials and the commission are not refunded.")));
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean materials = requiredItemsDropDownMenu != null && requiredItemsDropDownMenu.isMouseOver(mouseX, mouseY);
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (materials && handled) setFocused(requiredItemsDropDownMenu);
        return handled;
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (layout == null) return;
        var box = layout.panel();
        graphics.fill(box.x() - 1, box.y() - 1, box.right() + 1, box.bottom() + 1, 0xFF687487);
        graphics.fill(box.x(), box.y(), box.right(), box.bottom(), 0xF019202A);
    }
    @Override public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (layout == null) return;
        var panel = layout.panel(); int x = panel.x() + layout.padding();
        int textWidth = panel.width() - 2 * layout.padding();
        text(graphics, "Commissioned construction", x, panel.y() + 8, textWidth, 0xFFF0D0);
        if (panel.height() >= 150) text(graphics,
                buildArea.getWidthSize() + " wide  x  " + buildArea.getDepthSize() + " deep  x  " + buildArea.getHeightSize() + " high",
                x, panel.y() + 22, textWidth, 0xC8D0DB);
        if (layout.details()) text(graphics, "Progress: Building > Construction", x, panel.y() + 36, textWidth, 0xAAB8C8);
        if (layout.content()) text(graphics, previewFits ? "Drag: rotate. Right-drag: pan." : "Large plan: right-drag to pan.", layout.preview().x(),
                layout.projection().y() - 10, layout.preview().width(), 0xAAB8C8);
        else text(graphics, "Enlarge window for the native preview.", x, layout.preview().y(), textWidth, 0xAAB8C8);
    }
    private void text(GuiGraphics graphics, String value, int x, int y, int available, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(1, available)), x, y, color, false);
    }

    /** Same native material rendering/scrolling, with a keyboard route to its public handlers. */
    private static final class KeyboardMaterials extends DisplayTextItemScrollDropDownMenu {
        KeyboardMaterials(int x, int y, int width, int height, List<ItemStack> items) {
            super(ItemStack.EMPTY, "Materials", x, y, width, height, items, null);
        }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)) {
                onMouseClick(getX() + getWidth() / 2.0, getY() + getHeight() / 2.0); return true;
            }
            if (isFocused() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN))
                return mouseScrolled(getX(), getY(), key == GLFW.GLFW_KEY_UP ? 1 : -1);
            return super.keyPressed(key, scanCode, modifiers);
        }
    }
}
