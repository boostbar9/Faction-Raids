package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.nativecompat.ProtectedBuildArea;
import com.talhanation.workers.client.gui.BuildAreaScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.nativecompat.ProtectedConstructionActions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Native blueprint/material inspection, with sealed controls clearly read-only. */
public final class ProtectedConstructionScreen extends BuildAreaScreen {
    private Button cancel, projection;
    public ProtectedConstructionScreen(ProtectedBuildArea area, Player player) { super(area, player); }

    @Override public void setButtons() {
        super.setButtons();
        // Native mouseClicked dispatches this dropdown directly, bypassing widget.active.
        if (structureOptions != null) removeWidget(structureOptions);
        structureOptions = null;
        cancel = null; projection = null;
        // Preserve the native Delete control's layout using Screen's public
        // widget lifecycle, while replacing its unauthenticated packet action.
        for (var child : java.util.List.copyOf(children())) {
            if (!(child instanceof Button button)
                    || !(button.getMessage().getContents() instanceof TranslatableContents contents)
                    || !contents.getKey().equals("gui.workers.command.text.destroy")) continue;
            removeWidget(button);
            cancel = addRenderableWidget(Button.builder(button.getMessage(), ignored -> confirmCancel())
                    .bounds(button.getX(), button.getY(), button.getWidth(), button.getHeight()).build());
            break;
        }
        if (alwaysShowProjectionCheckBox != null) {
            var original = alwaysShowProjectionCheckBox;
            removeWidget(original);
            projection = addRenderableWidget(Button.builder(projectionLabel(), ignored ->
                    RaidNetwork.protectedConstructionAction(buildArea.getUUID(), buildArea.getAlwaysShowProjection()
                            ? ProtectedConstructionActions.HIDE : ProtectedConstructionActions.SHOW))
                    .bounds(original.getX(), original.getY(), original.getWidth(), original.getHeight()).build());
        }
        lockControls();
    }

    private void lockControls() {
        for (var child : children()) if (child instanceof AbstractWidget widget
                && child != structurePreview && child != requiredItemsDropDownMenu && child != cancel && child != projection)
            widget.active = false;
    }

    @Override public void resetScan() { /* Accepted plan remains the native inspection source. */ }
    @Override public void onAreaMoved() { /* Server marker and origin are sealed independently. */ }
    @Override public void tick() {
        super.tick(); lockControls();
        if (projection != null) projection.setMessage(projectionLabel());
        if (buildArea.isRemoved()) onClose();
    }
    private Component projectionLabel() {
        return Component.literal("Projection: " + (buildArea.getAlwaysShowProjection() ? "Always" : "Focus"));
    }
    private void confirmCancel() {
        if (minecraft == null) return;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                RaidNetwork.protectedConstructionAction(buildArea.getUUID(), ProtectedConstructionActions.CANCEL);
                minecraft.setScreen(null);
            } else minecraft.setScreen(this);
        }, Component.literal("Cancel this construction job?"),
                Component.literal("Placed blocks remain. Supplied materials and the commission are not refunded.")));
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        lockControls();
        super.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawCenteredString(font, Component.literal("Plan locked. Progress: Building > Construction"),
                width / 2, 8, 0xFFE0B0);
    }
}
