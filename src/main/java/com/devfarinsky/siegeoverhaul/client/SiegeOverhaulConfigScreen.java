package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Type-preserving settings editor. Edits are retained across layout changes and
 * nested screens, and committed only through the existing save-on-close path.
 */
public final class SiegeOverhaulConfigScreen extends Screen {
    private final Screen parent;
    private final ForgeConfigSpec spec;
    private final List<Entry> entries = new ArrayList<>();
    private final List<RowWidget> rows = new ArrayList<>();
    private List<Entry> shown = List.of();
    private boolean loaded;
    private ConfigScreenLayout layout;
    private int firstRow;
    private EditBox filterBox;
    private CoreButton done;
    private CoreButton heroVisuals;
    private String filter = "";
    private String focusKey = "filter";
    private int filterCursor;
    private boolean draggingScrollbar;
    private double thumbGrab;

    public SiegeOverhaulConfigScreen(Screen parent, ForgeConfigSpec spec) {
        super(Component.literal(spec == com.devfarinsky.siegeoverhaul.HeroVisualConfig.SPEC
                ? "Hero visuals" : "Siege Overhaul Settings"));
        this.parent = parent;
        this.spec = spec;
    }

    @Override
    protected void init() {
        rememberFocus();
        for (RowWidget row : rows) { row.visible = false; row.setFocused(false); }
        clearWidgets();
        setFocused(null);
        rows.clear();
        // Minecraft reinitializes the same screen after resize and a child screen.
        // Re-reading the spec here used to discard every uncommitted edit.
        if (!loaded) {
            collect("", spec.getValues().valueMap(), entries);
            loaded = true;
        }
        draggingScrollbar = false;
        layout = ConfigScreenLayout.fit(width, height);
        var bounds = layout.filter;
        filterBox = new EditBox(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                Component.literal("Filter settings by name or full path"));
        filterBox.setMaxLength(256);
        filterBox.setHint(Component.literal("Filter settings"));
        filterBox.setValue(filter);
        filterBox.moveCursorTo(Math.min(filterCursor, filter.length()));
        filterBox.setTextColor(CommandPalette.TEXT);
        filterBox.setResponder(this::filterChanged);
        addRenderableWidget(filterBox);

        boolean hasHero = spec == com.devfarinsky.siegeoverhaul.RaidConfig.SPEC;
        int buttonWidth = hasHero ? (bounds.width() - 8) / 2 : bounds.width();
        heroVisuals = null;
        if (hasHero) {
            heroVisuals = addRenderableWidget(new CoreButton(Component.literal("Hero visuals"), b -> {
                rememberFocus();
                minecraft.setScreen(new SiegeOverhaulConfigScreen(this, com.devfarinsky.siegeoverhaul.HeroVisualConfig.SPEC));
            }, bounds.x(), layout.footerY, buttonWidth, 20, false, () -> false));
        }
        done = addRenderableWidget(new CoreButton(Component.literal("Done"), b -> onClose(),
                hasHero ? bounds.right() - buttonWidth : bounds.x(), layout.footerY,
                buttonWidth, 20, false, () -> false).primary());
        shown = filtered();
        int focusedIndex = indexOfFocusKey(focusKey);
        firstRow = focusedIndex >= 0 ? layout.ensureVisible(firstRow, focusedIndex, shown.size())
                : layout.clampFirst(firstRow, shown.size());
        rebuildRows();
        restoreFocus();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        rememberFocus();
        super.resize(minecraft, width, height);
    }

    @Override
    public void removed() {
        rememberFocus();
        super.removed();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void collect(String prefix, Map<String, Object> map, List<Entry> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object value = e.getValue();
            if (value instanceof ForgeConfigSpec.ConfigValue<?> cfg) {
                out.add(new Entry(key, cfg, cfg.get()));
            } else if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig sub) {
                collect(key, (Map<String, Object>) (Map) sub.valueMap(), out);
            } else if (value instanceof Map<?, ?> sub) {
                collect(key, (Map<String, Object>) sub, out);
            }
        }
    }

    private List<Entry> filtered() {
        String query = filter.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) return List.copyOf(entries);
        return entries.stream().filter(e -> e.path.toLowerCase(Locale.ROOT).contains(query)
                || shortLabel(e.path).toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private void filterChanged(String value) {
        filter = value;
        firstRow = 0;
        draggingScrollbar = false;
        shown = filtered();
        setFocused(filterBox);
        rebuildRows();
    }

    private void rebuildRows() {
        for (RowWidget row : rows) {
            row.captureCursor();
            row.visible = false;
            row.setFocused(false);
            removeWidget(row); // Removes children AND narration registrations.
        }
        rows.clear();
        firstRow = layout.clampFirst(firstRow, shown.size());
        for (int slot = 0; slot < layout.capacity() && firstRow + slot < shown.size(); slot++) {
            var bounds = layout.input(slot);
            RowWidget row = buildRow(shown.get(firstRow + slot), bounds);
            rows.add(addWidget(row)); // Render manually, inside the viewport scissor.
        }
    }

    private RowWidget buildRow(Entry entry, ConfigScreenLayout.Bounds bounds) {
        AbstractWidget control;
        if (entry.pending instanceof Boolean || entry.pending instanceof Enum<?>) {
            control = new CoreButton(Component.literal(entry.displayValue()), button -> {
                entry.activate();
                button.setMessage(Component.literal(entry.displayValue()));
            }, bounds.x(), bounds.y(), bounds.width(), bounds.height(), false,
                    () -> Boolean.TRUE.equals(entry.pending));
        } else {
            EditBox box = new EditBox(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    Component.literal(entry.path));
            box.setMaxLength(ConfigTextCodec.editorLimit(entry.value, entry.text));
            if (entry.value instanceof List<?>) box.setHint(Component.literal("comma-separated; blank = none"));
            box.setValue(entry.text);
            box.moveCursorTo(Math.min(entry.cursor, entry.text.length()));
            box.setTextColor(entry.validText ? CommandPalette.TEXT : CommandPalette.ACCENT_BLOOD);
            box.setResponder(value -> {
                entry.edit(value);
                box.setTextColor(entry.validText ? CommandPalette.TEXT : CommandPalette.ACCENT_BLOOD);
            });
            control = box;
        }
        return new RowWidget(entry, control, layout.viewport);
    }

    @Override
    public void setFocused(GuiEventListener target) {
        // AbstractContainerEventHandler forwards focus changes to both children.
        super.setFocused(target);
        if (target != null) rememberFocus();
    }

    private void rememberFocus() {
        if (filterBox != null) filterCursor = filterBox.getCursorPosition();
        GuiEventListener focused = getFocused();
        if (focused instanceof RowWidget row) {
            row.captureCursor();
            focusKey = "entry:" + row.entry.path;
        } else if (focused != null) {
            if (focused == filterBox) focusKey = "filter";
            else if (focused == heroVisuals) focusKey = "hero";
            else if (focused == done) focusKey = "done";
        }
    }

    private void restoreFocus() {
        for (RowWidget row : rows) {
            if (("entry:" + row.entry.path).equals(focusKey)) { setFocused(row); return; }
        }
        if (focusKey.equals("hero") && heroVisuals != null) setFocused(heroVisuals);
        else if (focusKey.equals("done")) setFocused(done);
        else setFocused(filterBox);
    }

    private int indexOfFocusKey(String key) {
        return key.startsWith("entry:") ? indexOfPath(key.substring(6)) : -1;
    }

    private int indexOfPath(String path) {
        for (int i = 0; i < shown.size(); i++) if (shown.get(i).path.equals(path)) return i;
        return -1;
    }

    private void scrollTo(int first) {
        int target = layout.clampFirst(first, shown.size());
        if (target == firstRow) return;
        rememberFocus();
        firstRow = target;
        rebuildRows();
        restoreFocus(); // A hidden field cannot retain keyboard input.
    }

    private void focusEntry(int index) {
        if (layout.capacity() == 0 || index < 0 || index >= shown.size()) return;
        rememberFocus();
        firstRow = layout.ensureVisible(firstRow, index, shown.size());
        focusKey = "entry:" + shown.get(index).path;
        rebuildRows();
        restoreFocus();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            rememberFocus();
            // Include offscreen entries in logical Tab order, revealing each one
            // before giving it focus. Native focus search only sees visible rows.
            List<String> order = new ArrayList<>();
            order.add("filter");
            if (layout.capacity() > 0) for (Entry entry : shown) order.add("entry:" + entry.path);
            if (heroVisuals != null) order.add("hero");
            order.add("done");
            int current = order.indexOf(focusKey);
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            String next = order.get(Math.floorMod(current + step, order.size()));
            int index = indexOfFocusKey(next);
            if (index >= 0) focusEntry(index);
            else { focusKey = next; restoreFocus(); }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            int step = Math.max(1, layout.capacity()) * (keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1);
            if (getFocused() instanceof RowWidget row) {
                focusEntry(Math.max(0, Math.min(shown.size() - 1, indexOfPath(row.entry.path) + step)));
            } else scrollTo(firstRow + step);
            return true;
        }
        if (getFocused() instanceof RowWidget row && !(row.control instanceof EditBox)) {
            int index = indexOfPath(row.entry.path);
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                focusEntry(Math.max(0, Math.min(shown.size() - 1, index + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1))));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END) {
                focusEntry(keyCode == GLFW.GLFW_KEY_HOME ? 0 : shown.size() - 1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        filterBox.tick();
        for (RowWidget row : rows) if (row.control instanceof EditBox box) box.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        var window = layout.window;
        CommandFrame.window(g, window.x(), window.y(), window.width(), window.height());
        CommandFrame.header(g, window.x(), window.y(), window.width(), 22);
        drawFitted(g, title.getString(), layout.filter.x(), window.y() + 10, layout.filter.width(), CommandPalette.TEXT);
        int count = shown.size();
        String matches = layout.capacity() == 0 ? "Increase window height to edit"
                : count == 0 ? "0 matching settings"
                : (firstRow + 1) + "–" + Math.min(count, firstRow + rows.size()) + " of " + count
                + (filter.isBlank() ? " settings" : " matches");
        drawFitted(g, matches, layout.filter.x(), layout.filter.bottom() + 6, layout.filter.width(), CommandPalette.TEXT_MUTED);
        drawFitted(g, "Changes save on close", layout.filter.x(), layout.footerY - 12, layout.filter.width(), CommandPalette.TEXT_DIM);

        var viewport = layout.viewport;
        CommandFrame.surface(g, viewport.x(), viewport.y(), viewport.width(), viewport.height());
        g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            for (int slot = 0; slot < rows.size(); slot++) {
                RowWidget row = rows.get(slot);
                var label = layout.label(slot);
                drawFitted(g, shortLabel(row.entry.path), label.x(), label.y(), label.width(), CommandPalette.TEXT_MUTED);
                row.render(g, mouseX, mouseY, partialTick);
            }
            if (shown.isEmpty()) {
                drawFitted(g, entries.isEmpty() ? "No settings available" : "No matching settings", viewport.x() + 8,
                        viewport.y() + 12, viewport.width() - 16, CommandPalette.TEXT);
                if (!entries.isEmpty()) drawFitted(g, "Try a shorter name or clear the filter.", viewport.x() + 8,
                        viewport.y() + 26, viewport.width() - 16, CommandPalette.TEXT_MUTED);
            } else if (layout.capacity() == 0) {
                drawFitted(g, "Increase the window height to edit.", viewport.x() + 4, viewport.y() + 2,
                        viewport.width() - 8, CommandPalette.TEXT_MUTED);
            }
        } finally {
            g.disableScissor();
        }
        if (layout.maxFirst(count) > 0 && layout.capacity() > 0) {
            var track = layout.scrollbar;
            var thumb = layout.thumb(firstRow, count);
            g.fill(track.x(), track.y(), track.right(), track.bottom(), CommandPalette.CHIP_FILL);
            g.fill(thumb.x(), thumb.y(), thumb.right(), thumb.bottom(), CommandPalette.ACCENT_TEAL);
        }
        // Only fixed controls are renderables; row widgets never escape scissor.
        super.render(g, mouseX, mouseY, partialTick);
        if (viewport.contains(mouseX, mouseY)) {
            int slot = (mouseY - viewport.y()) / layout.rowHeight;
            if (slot >= 0 && slot < rows.size() && (layout.label(slot).contains(mouseX, mouseY)
                    || rows.get(slot).isMouseOver(mouseX, mouseY))) {
                Entry entry = rows.get(slot).entry;
                String comment = spec.getLevelComment(List.of(entry.path.split("\\.")));
                String tooltip = entry.path + (comment == null || comment.isBlank() ? "" : "\n" + comment);
                if (!entry.validText) tooltip += "\nInvalid number; the last valid value will be saved.";
                g.renderTooltip(font, font.split(Component.literal(tooltip), Math.max(40, Math.min(320, width - 24))), mouseX, mouseY);
            }
        }
    }

    private void drawFitted(GuiGraphics g, String value, int x, int y, int width, int color) {
        if (width <= 0) return;
        String fitted = font.plainSubstrByWidth(value, width);
        if (!fitted.equals(value) && font.width("…") <= width) {
            fitted = font.plainSubstrByWidth(value, width - font.width("…")) + "…";
        }
        g.drawString(font, fitted, x, y, color, false);
    }

    static String shortLabel(String path) {
        int dot = path.lastIndexOf('.');
        String tail = dot < 0 ? path : path.substring(dot + 1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(tail.charAt(i - 1))) out.append(' ');
            out.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return out.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (layout.viewport.contains(mouseX, mouseY) || layout.scrollbar.contains(mouseX, mouseY)) {
            if (delta != 0) scrollTo(firstRow + (delta > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && layout.capacity() > 0 && layout.maxFirst(shown.size()) > 0
                && layout.scrollbar.contains(mouseX, mouseY)) {
            var thumb = layout.thumb(firstRow, shown.size());
            thumbGrab = thumb.contains(mouseX, mouseY) ? mouseY - thumb.y() : thumb.height() / 2.0;
            draggingScrollbar = true;
            scrollTo(layout.firstAtThumb(mouseY - thumbGrab, shown.size()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            scrollTo(layout.firstAtThumb(mouseY - thumbGrab, shown.size()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        for (Entry entry : entries) entry.commit();
        spec.save();
        minecraft.setScreen(parent);
    }

    static final class Entry {
        final String path;
        @SuppressWarnings("rawtypes") final ForgeConfigSpec.ConfigValue cfg;
        Object value;
        Object pending;
        String text;
        int cursor;
        boolean validText = true;

        @SuppressWarnings("rawtypes")
        Entry(String path, ForgeConfigSpec.ConfigValue cfg, Object value) {
            this.path = path;
            this.cfg = cfg;
            this.value = value;
            pending = value;
            text = ConfigTextCodec.format(value);
            cursor = text.length();
        }

        void edit(String text) {
            this.text = text; // Keep incomplete numeric input through resize/filtering.
            Object parsed = ConfigTextCodec.parse(text, value);
            validText = parsed != null;
            if (validText) pending = parsed;
        }

        void activate() {
            if (pending instanceof Boolean state) pending = !state;
            else if (pending instanceof Enum<?> current) {
                Object[] values = current.getDeclaringClass().getEnumConstants();
                pending = values[(current.ordinal() + 1) % values.length];
            }
        }

        String displayValue() {
            return pending instanceof Boolean state ? (state ? "Enabled" : "Disabled") : String.valueOf(pending);
        }

        @SuppressWarnings("unchecked")
        void commit() {
            if (pending != null && !pending.equals(value)) {
                cfg.set(pending);
                value = pending;
            }
        }
    }

    /** Focus/narration bridge; only fully visible rows are registered on Screen. */
    static final class RowWidget extends AbstractWidget {
        final Entry entry;
        final AbstractWidget control;
        final ConfigScreenLayout.Bounds viewport;

        RowWidget(Entry entry, AbstractWidget control, ConfigScreenLayout.Bounds viewport) {
            super(control.getX(), control.getY(), control.getWidth(), control.getHeight(), Component.literal(entry.path));
            this.entry = entry;
            this.control = control;
            this.viewport = viewport;
        }

        private boolean available() {
            return visible && active && control.visible && control.active
                    && getX() >= viewport.x() && getY() >= viewport.y()
                    && getX() + width <= viewport.right() && getY() + height <= viewport.bottom();
        }

        void captureCursor() {
            if (control instanceof EditBox box) entry.cursor = box.getCursorPosition();
        }

        @Override public void setFocused(boolean focused) {
            captureCursor();
            super.setFocused(focused && available());
            control.setFocused(focused && available());
        }
        @Override public boolean isMouseOver(double x, double y) {
            return available() && viewport.contains(x, y) && control.isMouseOver(x, y);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            return isMouseOver(x, y) && control.mouseClicked(x, y, button);
        }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            return available() && isFocused() && viewport.contains(x, y) && control.mouseDragged(x, y, button, dx, dy);
        }
        @Override public boolean mouseReleased(double x, double y, int button) {
            return available() && control.mouseReleased(x, y, button);
        }
        @Override public boolean keyPressed(int key, int scan, int mods) {
            return available() && isFocused() && control.keyPressed(key, scan, mods);
        }
        @Override public boolean charTyped(char value, int mods) {
            return available() && isFocused() && control.charTyped(value, mods);
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!available()) return;
            if (isFocused()) {
                g.fill(getX() - 1, getY() - 1, getX() + width + 1, getY(), CommandPalette.ACCENT_TEAL);
                g.fill(getX() - 1, getY() + height, getX() + width + 1, getY() + height + 1, CommandPalette.ACCENT_TEAL);
                g.fill(getX() - 1, getY(), getX(), getY() + height, CommandPalette.ACCENT_TEAL);
                g.fill(getX() + width, getY(), getX() + width + 1, getY() + height, CommandPalette.ACCENT_TEAL);
            }
            control.render(g, mouseX, mouseY, partialTick);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput out) {
            if (!available()) return;
            String value = control instanceof EditBox ? entry.text : entry.displayValue();
            out.add(NarratedElementType.TITLE, Component.literal(entry.path + ": " + value));
            out.add(NarratedElementType.USAGE, Component.literal(control instanceof EditBox
                    ? "Type to edit. Tab moves to the next setting. Page Up and Page Down scroll settings."
                    : "Press Enter or Space to change. Tab moves to the next setting. Page Up and Page Down scroll settings."));
            if (!entry.validText) out.add(NarratedElementType.HINT,
                    Component.literal("Invalid number. The last valid value will be saved on close."));
        }
    }
}
