package com.devfarinsky.siegeoverhaul.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v4.18.0 auto-generated config screen for {@link com.devfarinsky.siegeoverhaul.RaidConfig}.
 *
 * <p>Walks the {@link ForgeConfigSpec} tree once at construction and builds
 * one entry widget per leaf: a click-to-toggle button for booleans, a text
 * field with numeric validation for ints and doubles, a cycle button for
 * enums, and a comma-separated text field for string lists. Scrolls a viewport instead
 * of trying to render 150+ rows onto one screen. Writes changes back to the
 * spec immediately so nothing gets lost if the player alt-tabs out.
 */
public final class SiegeOverhaulConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int ROW_PADDING = 4;
    private static final int LABEL_WIDTH = 220;
    private static final int WIDGET_WIDTH = 180;

    private final Screen parent;
    private final ForgeConfigSpec spec;
    private final List<Entry> entries = new ArrayList<>();

    private int scrollOffset;
    private int maxScroll;
    private int viewportTop;
    private int viewportHeight;
    private EditBox filterBox;
    private String filter = "";

    public SiegeOverhaulConfigScreen(Screen parent, ForgeConfigSpec spec) {
        super(Component.literal("Siege Overhaul Settings"));
        this.parent = parent;
        this.spec = spec;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void init() {
        entries.clear();
        collect("", spec.getValues().valueMap(), entries);

        int filterWidth = 240;
        filterBox = new EditBox(this.font, this.width / 2 - filterWidth / 2, 26,
                filterWidth, 18, Component.literal("Filter"));
        filterBox.setHint(Component.literal("Filter settings"));
        filterBox.setValue(filter);
        filterBox.setResponder(v -> { filter = v.toLowerCase(); scrollOffset = 0; layoutWidgets(); });
        addRenderableWidget(filterBox);

        viewportTop = 54;
        viewportHeight = this.height - viewportTop - 34;

        addRenderableWidget(Button.builder(Component.literal("Done"),
                b -> onClose()).bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());

        layoutWidgets();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void collect(String prefix, Map<String, Object> map, List<Entry> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?> cfg) {
                Object raw = cfg.get();
                out.add(new Entry(key, cfg, raw));
            } else if (v instanceof com.electronwill.nightconfig.core.UnmodifiableConfig sub) {
                collect(key, (Map<String, Object>) (Map) sub.valueMap(), out);
            } else if (v instanceof Map<?, ?> submap) {
                collect(key, (Map<String, Object>) submap, out);
            }
        }
    }

    private List<Entry> filtered() {
        if (filter.isEmpty()) return entries;
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.path.toLowerCase().contains(filter)) out.add(e);
        return out;
    }

    private void layoutWidgets() {
        // Remove old row widgets (keep filter + Done).
        this.children().removeIf(c -> c instanceof RowWidget);
        this.renderables.removeIf(r -> r instanceof RowWidget);

        List<Entry> shown = filtered();
        int totalHeight = shown.size() * (ROW_HEIGHT + ROW_PADDING);
        maxScroll = Math.max(0, totalHeight - viewportHeight);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        int x = this.width / 2 - (LABEL_WIDTH + WIDGET_WIDTH + 12) / 2 + LABEL_WIDTH + 12;
        int i = 0;
        for (Entry e : shown) {
            int y = viewportTop + i * (ROW_HEIGHT + ROW_PADDING) - scrollOffset;
            RowWidget w = buildRow(e, x, y);
            addRenderableWidget(w);
            i++;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RowWidget buildRow(Entry e, int x, int y) {
        Object v = e.value;
        if (v instanceof Boolean b) {
            return new ToggleRow(e, x, y, WIDGET_WIDTH, ROW_HEIGHT, b);
        }
        if (v instanceof Enum<?>) {
            return new EnumRow(e, x, y, WIDGET_WIDTH, ROW_HEIGHT);
        }
        // Numbers, strings, and string lists all use a type-preserving text row.
        return new TextRow(this.font, e, x, y, WIDGET_WIDTH, ROW_HEIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        // Scissor to the viewport so scrolled rows don't bleed over the header/footer.
        g.enableScissor(0, viewportTop, this.width, viewportTop + viewportHeight);
        // Row labels: draw here so they scroll with the widgets.
        int labelX = this.width / 2 - (LABEL_WIDTH + WIDGET_WIDTH + 12) / 2;
        List<Entry> shown = filtered();
        int i = 0;
        for (Entry e : shown) {
            int y = viewportTop + i * (ROW_HEIGHT + ROW_PADDING) - scrollOffset;
            if (y + ROW_HEIGHT >= viewportTop && y <= viewportTop + viewportHeight) {
                String label = shortLabel(e.path);
                g.drawString(this.font, label, labelX + 4, y + 7, 0xE0E0E0, false);
            }
            i++;
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, partialTick);

        // Scrollbar on the right if content overflows.
        if (maxScroll > 0) {
            int barX = this.width - 6;
            int barTop = viewportTop;
            int barBottom = viewportTop + viewportHeight;
            g.fill(barX, barTop, barX + 3, barBottom, 0x40000000);
            int thumbHeight = Math.max(20, viewportHeight * viewportHeight / (viewportHeight + maxScroll));
            int thumbY = barTop + (viewportHeight - thumbHeight) * scrollOffset / maxScroll;
            g.fill(barX, thumbY, barX + 3, thumbY + thumbHeight, 0xFFAA7F2A);
        }

        // Tooltip on the hovered label.
        int labelXX = this.width / 2 - (LABEL_WIDTH + WIDGET_WIDTH + 12) / 2;
        if (mouseX >= labelXX && mouseX < labelXX + LABEL_WIDTH
                && mouseY >= viewportTop && mouseY < viewportTop + viewportHeight) {
            int idx = (mouseY - viewportTop + scrollOffset) / (ROW_HEIGHT + ROW_PADDING);
            if (idx >= 0 && idx < shown.size()) {
                Entry e = shown.get(idx);
                String comment = spec.getLevelComment(splitPath(e.path));
                if (comment != null && !comment.isEmpty()) {
                    g.renderTooltip(this.font, this.font.split(Component.literal(comment), 260), mouseX, mouseY);
                }
            }
        }
    }

    private static List<String> splitPath(String path) {
        return List.of(path.split("\\."));
    }

    private static String shortLabel(String path) {
        int dot = path.lastIndexOf('.');
        String tail = dot < 0 ? path : path.substring(dot + 1);
        // Split camelCase: "coneFallbackEnabled" -> "Cone Fallback Enabled".
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(tail.charAt(i - 1))) out.append(' ');
            if (i == 0) out.append(Character.toUpperCase(c));
            else out.append(c);
        }
        return out.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= viewportTop && mouseY <= viewportTop + viewportHeight) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (delta * 20)));
            layoutWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        // Save any pending edits.
        for (Entry e : entries) e.commit();
        spec.save();
        this.minecraft.setScreen(parent);
    }

    // ----- entry model -----

    static final class Entry {
        final String path;
        @SuppressWarnings("rawtypes")
        final net.minecraftforge.common.ForgeConfigSpec.ConfigValue cfg;
        Object value;
        Object pending;

        @SuppressWarnings("rawtypes")
        Entry(String path, net.minecraftforge.common.ForgeConfigSpec.ConfigValue cfg, Object value) {
            this.path = path;
            this.cfg = cfg;
            this.value = value;
            this.pending = value;
        }

        @SuppressWarnings("unchecked")
        void commit() {
            if (pending != null && !pending.equals(value)) {
                cfg.set(pending);
                value = pending;
            }
        }
    }

    // ----- row widgets -----

    abstract static class RowWidget extends net.minecraft.client.gui.components.AbstractWidget {
        protected final Entry entry;

        RowWidget(Entry entry, int x, int y, int w, int h) {
            super(x, y, w, h, Component.literal(entry.path));
            this.entry = entry;
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {}
    }

    static final class ToggleRow extends RowWidget {
        boolean state;
        ToggleRow(Entry entry, int x, int y, int w, int h, boolean initial) {
            super(entry, x, y, w, h);
            this.state = initial;
        }
        @Override
        public void onClick(double mouseX, double mouseY) {
            state = !state;
            entry.pending = state;
        }
        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int fill = state ? 0xFF2E7D32 : 0xFF7B1F1F;
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fill);
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, 0xFF000000);
            g.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, 0xFF000000);
            String label = state ? "Enabled" : "Disabled";
            g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, label,
                    this.getX() + this.width / 2, this.getY() + 7, 0xFFFFFF);
        }
    }

    static final class EnumRow extends RowWidget {
        @SuppressWarnings({"rawtypes", "unchecked"})
        EnumRow(Entry entry, int x, int y, int w, int h) {
            super(entry, x, y, w, h);
        }
        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void onClick(double mouseX, double mouseY) {
            Enum current = (Enum) entry.pending;
            Object[] values = current.getDeclaringClass().getEnumConstants();
            int next = (current.ordinal() + 1) % values.length;
            entry.pending = values[next];
        }
        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF3A3A55);
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, 0xFF000000);
            g.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, 0xFF000000);
            g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                    entry.pending.toString(), this.getX() + this.width / 2, this.getY() + 7, 0xFFFFFF);
        }
    }

    static final class TextRow extends RowWidget {
        final EditBox box;
        TextRow(net.minecraft.client.gui.Font font, Entry entry, int x, int y, int w, int h) {
            super(entry, x, y, w, h);
            this.box = new EditBox(font, x + 1, y + 2, w - 2, h - 4, Component.literal(entry.path));
            String formatted = ConfigTextCodec.format(entry.pending);
            this.box.setMaxLength(ConfigTextCodec.editorLimit(entry.value, formatted));
            if (entry.value instanceof List<?>) {
                this.box.setHint(Component.literal("comma-separated; blank = none"));
            }
            // Raise the limit before loading the value: EditBox otherwise
            // truncates long existing lists to its vanilla default length.
            this.box.setValue(formatted);
            this.box.setResponder(v -> {
                Object parsed = ConfigTextCodec.parse(v, entry.value);
                if (parsed != null) entry.pending = parsed;
            });
        }
        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            this.box.setX(this.getX() + 1);
            this.box.setY(this.getY() + 2);
            this.box.render(g, mouseX, mouseY, partialTick);
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean hit = this.box.mouseClicked(mouseX, mouseY, button);
            this.box.setFocused(hit);
            return hit;
        }
        @Override
        public boolean charTyped(char c, int mods) { return this.box.charTyped(c, mods); }
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return this.box.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
