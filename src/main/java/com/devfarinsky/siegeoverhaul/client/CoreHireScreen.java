package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.core.*;
import com.devfarinsky.siegeoverhaul.siege.SiegeIntegration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command Center HUD — medieval-fantasy but modern-sleek presentation.
 *
 * <p>Purchase authority remains entirely on the server; this screen only
 * paints server-authoritative state from {@link CoreHireMenu} and dispatches
 * inventory-button clicks or {@link RaidNetwork} packets. Server contracts remain
 * unchanged. Navigation metadata and bounded tab windows allow new pages without
 * shrinking existing controls. The shared
 * presentation layer uses {@link CommandFrame} and
 * {@link CommandPalette}. Controls use readable labels; only real Minecraft
 * item sprites are used where an icon communicates a concrete item.
 *
 * <p>Seven tabs share the window:
 * <ul>
 *   <li><b>Army &amp; Heroes</b> — four rotating hire cards with live armored
 *       entity previews, readable kit details and siege deployment kits.</li>
 *   <li><b>Loot &amp; Blessings</b> — three mystery-loot cards with animated
 *       reveal reel and three blessing cards with cool-down state.</li>
 *   <li><b>Treasury</b> — deposit/withdraw controls, glanceable financial
 *       metrics, a scrollable roster and recent activity.</li>
 *   <li><b>Territory</b> — permanent faction-wide upgrades.</li>
 *   <li><b>Building</b> — perimeter planning, structure plans and nearby construction.</li>
 *   <li><b>Civilians</b> — faction residents and taxes.</li>
 *   <li><b>Intel</b> — units, enemy lore and the field playbook.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CoreHireScreen extends AbstractContainerScreen<CoreHireMenu> {

    private static final String FEEDBACK_URL =
            "https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/comments";
    private static final CoreCommandPage[] PAGES = CoreCommandPage.values();
    private CoreHireLayout layout;
    private CoreCommandPage tab = CoreCommandPage.ARMY;
    private final Button[] pageButtons = new Button[PAGES.length];
    private final Button[] intelSections = new Button[3];
    private Button previousPage, nextPage, treasuryShortcut, civilianRecruit;
    private EditBox intelSearch;
    private Button clearIntelSearch;
    private String intelQuery = "";
    private final int[] intelOffsets = new int[3];
    private int confirmBox = -1;
    private int seenLoot;
    private int revealBox = -1;
    private int revealTicks;
    private int waitingTicks;
    private int rosterOffset;
    /** Intel tab: 0 = Units, 1 = Enemy Lore, 2 = How to Play. */
    private int intelSection;
    private int intelOffset;
    /** Last-drawn intel body geometry, cached so mouseClicked/mouseDragged can hit-test the scrollbar. */
    private int intelBodyX, intelBodyY, intelBodyW, intelBodyH;
    /** Last-computed max scroll offset so the scrollbar drag can map cleanly. */
    private int intelMaxOffset;
    /** Drag state for the intel scrollbar thumb. */
    private boolean intelDragging;
    private int intelDragGrabY;
    private int intelDragStartOffset;
    private ItemStack revealed = ItemStack.EMPTY;
    private int revealedTier;

    private final Button[] hire = new Button[4];
    private final Button[] siegeYard = new Button[2];
    private final Button[] territoryBuffs = new Button[4];
    private final Button[] buildingSections = new Button[BuildingSection.values().length];
    private final Button[] defensePlans = new Button[DefenseBlueprint.Kind.values().length];
    private Button takeDefensePlan, previousPlans, nextPlans, reviewPerimeter, constructionPrevious, constructionNext, constructionCancel;
    private int planPage;
    private ConstructionReport.Job displayedCancellationTarget;
    private BuildingSection buildingSection = BuildingSection.PERIMETER;
    private DefenseBlueprint.Kind selectedDefense = DefenseBlueprint.Kind.WALL;
    private final BuildingReportSubscription buildingReport = new BuildingReportSubscription();
    // Match the existing manual wall's cobblestone/oak template on first review.
    private int perimeterMaterial = 1;
    private int constructionPage;
    private int planRequestCooldown, perimeterRequestCooldown, cancellationCooldown;
    private final Button[] fortifyButtons = new Button[TerritoryFortification.MATERIALS.length];
    private final BuildingPlanThumbnail[] perimeterExamples = new BuildingPlanThumbnail[TerritoryFortification.MATERIALS.length];
    private final Button[] boxes = new Button[3];
    private final Button[] buffs = new Button[3];
    private final Button[] bank = new Button[4];
    private Button feedbackButton;

    /** Bounded caches avoid remeasuring and rewrapping unchanged labels every frame. */
    private final Map<TextMeasureKey, String> fittedText = boundedCache(256);
    private final Map<TextMeasureKey, List<FormattedCharSequence>> wrappedText = boundedCache(256);

    private record TextMeasureKey(String value, int width) {}

    private static <K, V> Map<K, V> boundedCache(int maximumSize) {
        return new LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maximumSize;
            }
        };
    }

    public CoreHireScreen(CoreHireMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(CoreMenus.HIRING.get(), CoreHireScreen::new));
    }

    private void action(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void init() {
        fittedText.clear();
        wrappedText.clear();
        intelDragging = false;
        intelBodyW = intelBodyH = intelMaxOffset = 0;
        layout = CoreHireLayout.fit(width, height);
        imageWidth = layout.width();
        imageHeight = layout.height();
        super.init();

        // Page metadata and the strip own navigation; purchase IDs stay server-owned.
        for (int i = 0; i < PAGES.length; i++) {
            final CoreCommandPage page = PAGES[i];
            pageButtons[i] = addRenderableWidget(new CoreButton(
                    Component.literal(page.label()), b -> selectPage(page),
                    0, layout.tabY(), 72, CoreHireLayout.TAB_HEIGHT,
                    true, () -> tab == page));
        }
        previousPage = addRenderableWidget(new CoreButton(Component.literal("<"),
                b -> movePage(-1), 0, layout.tabY(), CoreTabStrip.ARROW_WIDTH,
                CoreHireLayout.TAB_HEIGHT, false, () -> false));
        nextPage = addRenderableWidget(new CoreButton(Component.literal(">"),
                b -> movePage(1), 0, layout.tabY(), CoreTabStrip.ARROW_WIDTH,
                CoreHireLayout.TAB_HEIGHT, false, () -> false));
        treasuryShortcut = addRenderableWidget(new CoreButton(Component.literal(""),
                b -> selectPage(CoreCommandPage.TREASURY), layout.treasuryX(), layout.y() + 6,
                layout.treasuryWidth(), 24, false, () -> tab == CoreCommandPage.TREASURY));
        treasuryShortcut.setMessage(Component.literal("Treasury"));
        String[] archiveLabels = {"Units", "Enemy Lore", "How to Play"};
        int archiveWidth = (layout.width() - 20) / 3;
        for (int i = 0; i < intelSections.length; i++) {
            final int section = i;
            intelSections[i] = addRenderableWidget(new CoreButton(Component.literal(archiveLabels[i]),
                    b -> selectIntelSection(section), layout.x() + 10 + i * archiveWidth,
                    layout.contentY(), archiveWidth - 2, 18, true, () -> intelSection == section));
        }
        intelSearch = addRenderableWidget(new EditBox(font, layout.x() + 11,
                layout.contentY() + 23, layout.width() - 73, 18,
                Component.literal("Search current Intel section")));
        intelSearch.setMaxLength(80);
        intelSearch.setHint(Component.literal("Search this section..."));
        intelSearch.setValue(intelQuery);
        intelSearch.setResponder(this::filterIntel);
        clearIntelSearch = addRenderableWidget(new CoreButton(Component.literal("Clear"),
                b -> intelSearch.setValue(""), layout.x() + layout.width() - 57,
                layout.contentY() + 23, 46, 18, false, () -> false));
        civilianRecruit = addRenderableWidget(new CoreButton(Component.literal("House a civilian · 16e"),
                b -> action(86),layout.x()+12,layout.contentBottom()-24,layout.width()-24,20,false,()->false));
        updateNavigation();

        // Close (X) button in the header for players who can't reach Escape
        // (e.g. controller users, one-handed play, remap conflicts).
        addRenderableWidget(new CoreButton(
                Component.literal("X"),
                b -> onClose(),
                layout.x() + layout.width() - 22, layout.y() + 6,
                16, 16,
                false, () -> false).hint("Close this menu."));

        // Hire keys and bank keys share the same iteration to stay compact.
        for (int i = 0; i < 4; i++) {
            final int index = i;
            // Portrait occupies the left ~80px of the card; the hire button
            // sits under the info column on the right.
            int hirePortrait = hirePortraitSize();
            int hireX = layout.compact()
                    ? layout.cardX(i) + hirePortrait + 11
                    : layout.cardX(i) + hirePortrait + 14;
            int hireY = layout.compact()
                    ? layout.cardY(i) + layout.cardHeight() - 18
                    : layout.cardY(i) + layout.cardHeight() - 25;
            int hireW = layout.compact()
                    ? layout.cardWidth() - hirePortrait - 17
                    : layout.cardWidth() - hirePortrait - 22;
            int hireH = layout.compact() ? 14 : 20;
            hire[i] = addRenderableWidget(new CoreButton(
                    Component.literal("Hire"),
                    b -> RaidNetwork.purchaseCoreOffer(menu.containerId, index, menu.rotation()),
                    hireX, hireY, hireW, hireH,
                    false, () -> false));

            String[] bankLabels = layout.compact()
                    ? new String[]{"Deposit 8", "Deposit 64", "Withdraw 8", "Withdraw 64"}
                    : new String[]{"Deposit 8", "Deposit 64", "Withdraw 8", "Withdraw 64"};
            int bankGap = layout.controlGap();
            int bw = (layout.width() - layout.outerMargin() * 2 - bankGap * 3) / 4;
            bank[i] = addRenderableWidget(new CoreButton(
                    Component.literal(bankLabels[i]),
                    b -> action(40 + index),
                    layout.x() + layout.outerMargin() + i * (bw + bankGap),
                    layout.contentY() + 50,
                    bw, 20,
                    false, () -> false, layout.compact() ? ItemStack.EMPTY : ItemIcons.EMERALD));
        }

        // Siege Yard buttons issue paid placement kits. The player then
        // right-clicks a clear outdoor area, avoiding failed auto-spawns when
        // the Core is indoors or boxed in by furniture/walls.
        int yardGap = layout.controlGap();
        int yardW = (layout.width() - layout.outerMargin() * 2 - yardGap) / 2;
        for (int i = 0; i < 2; i++) {
            final int index = i;
            siegeYard[i] = addRenderableWidget(new CoreButton(
                    Component.literal(SiegeYard.LABELS[i] + "  " + SiegeYard.PRICES[i]),
                    b -> action(50 + index),
                    layout.x() + layout.outerMargin() + i * (yardW + yardGap),
                    layout.siegeYardY(),
                    yardW, CoreHireLayout.ARMY_ACTION_HEIGHT,
                    false, () -> false));
        }

        // Territory-level buff buttons: four one-time upgrades that apply
        // faction-wide effects. Placed as a 2x2 grid of tall purchase cards
        // that fill the tab body. No map, no zoom controls; this tab is a
        // pure upgrade shop for kingdom-wide territory buffs.
        int tbCols = 2;
        int tbGridTop = layout.contentY();
        int tbCellW = layout.territoryCardWidth();
        int tbCellH = layout.territoryCardHeight();
        // Purchase button lives inside its card, near the bottom-right.
        int btnH = 18;
        for (int i = 0; i < TerritoryBuffs.COUNT; i++) {
            final int index = i;
            int col = i % tbCols, row = i / tbCols;
            int cellX = layout.x() + 10 + col * (tbCellW + 8);
            int cellY = tbGridTop + row * (tbCellH + 8);
            int btnW = Math.min(140, tbCellW - 16);
            territoryBuffs[i] = addRenderableWidget(new CoreButton(
                    Component.literal(TerritoryBuffs.LABELS[i]),
                    b -> action(60 + index),
                    cellX + tbCellW - btnW - 8,
                    cellY + tbCellH - btnH - 6,
                    btnW, btnH,
                    false, () -> false));
        }
        // Building has a single navigation band; every body view owns the same
        // bounded area. Selection never purchases or starts a builder job.
        var building = buildingLayout();
        for (BuildingSection section : BuildingSection.values()) {
            int index = section.ordinal();
            buildingSections[index] = addRenderableWidget(new CoreButton(Component.literal(
                    building.sectionWidth() < 110 ? section.compactLabel : section.label),
                    b -> selectBuildingSection(section), building.sectionX(index), building.sectionY(),
                    building.sectionWidth(), CoreBuildingLayout.SECTION_HEIGHT, true,
                    () -> buildingSection == section));
        }
        for (int i = 0; i < fortifyButtons.length; i++) {
            final int index = i;
            fortifyButtons[i] = addRenderableWidget(new CoreButton(
                    Component.literal(TerritoryFortification.material(i).label()),
                    b -> { perimeterMaterial = index; updateControlState(); },
                    building.materialX(i, fortifyButtons.length), building.materialY(i),
                    building.materialWidth(fortifyButtons.length), CoreBuildingLayout.ACTION_HEIGHT,
                    false, () -> perimeterMaterial == index));
        }
        reviewPerimeter = addRenderableWidget(new CoreButton(Component.literal("Review in world"),
                b -> {
                    if (perimeterRequestCooldown > 0) return;
                    perimeterRequestCooldown = 10;
                    action(70 + perimeterMaterial);
                    updateControlState();
                }, building.perimeterActionX(), building.perimeterActionY(), building.perimeterActionWidth(),
                CoreBuildingLayout.ACTION_HEIGHT, false, () -> false).primary());
        for (DefenseBlueprint.Kind kind : DefenseBlueprint.Kind.values()) {
            int index = kind.ordinal();
            defensePlans[index] = addRenderableWidget(new BuildingPlanButton(kind,
                    b -> selectDefense(kind), building.planX(index), building.planY(index),
                    building.planWidth(), building.planHeight(), () -> selectedDefense == kind));
        }
        planPage = selectedDefense.ordinal() / building.plansPerPage();
        previousPlans = addRenderableWidget(new CoreButton(Component.literal("‹"),
                b -> movePlanPage(-1), building.x(), building.actionY(), 28,
                CoreBuildingLayout.ACTION_HEIGHT, false, () -> false).hint("Previous structure plans."));
        nextPlans = addRenderableWidget(new CoreButton(Component.literal("›"),
                b -> movePlanPage(1), building.x() + building.width() - 28, building.actionY(), 28,
                CoreBuildingLayout.ACTION_HEIGHT, false, () -> false).hint("Next structure plans."));
        takeDefensePlan = addRenderableWidget(new CoreButton(Component.literal("Take free plan"),
                b -> {
                    if (planRequestCooldown > 0) return;
                    planRequestCooldown = 10;
                    action(90 + selectedDefense.ordinal());
                    updateControlState();
                }, building.planActionX(), building.actionY(), building.planActionWidth(),
                CoreBuildingLayout.ACTION_HEIGHT, false, () -> false).primary());
        constructionPrevious = addRenderableWidget(new CoreButton(Component.literal("< Previous"),
                b -> { constructionPage--; updateControlState(); }, building.x(),
                building.actionY(), building.reportNavigationWidth(), CoreBuildingLayout.ACTION_HEIGHT,
                false, () -> false));
        constructionNext = addRenderableWidget(new CoreButton(Component.literal("Next >"),
                b -> { constructionPage++; updateControlState(); },
                building.x() + building.reportNavigationWidth() + CoreBuildingLayout.GAP,
                building.actionY(), building.reportNavigationWidth(), CoreBuildingLayout.ACTION_HEIGHT,
                false, () -> false));

        constructionCancel = addRenderableWidget(new CoreButton(Component.literal("Cancel entire perimeter"),
                b -> confirmProjectCancellation(), building.reportCancelX(), building.reportCancelY(),
                building.reportCancelWidth(), CoreBuildingLayout.ACTION_HEIGHT, false, () -> false));

        // Persistent path for beta feedback. Minecraft shows its normal
        // external-link confirmation before opening CurseForge comments.
        int feedbackW = layout.feedbackWidth();
        feedbackButton = addRenderableWidget(new CoreButton(
                Component.literal(layout.compact() ? "Feedback" : "Leave Feedback"),
                b -> ConfirmLinkScreen.confirmLinkNow(FEEDBACK_URL, this, true),
                layout.x() + layout.width() - feedbackW - 8,
                layout.footerY() + 2,
                feedbackW, 14,
                false, () -> false));

        // Loot boxes and blessing keys.
        for (int i = 0; i < 3; i++) {
            final int index = i;
            int keyY = layout.marketY(i) + layout.marketHeight() - (layout.compact() ? 17 : 21);
            int keyXInset = layout.compact() ? 23 : 7;
            int keyHeight = layout.compact() ? 14 : 17;
            boxes[i] = addRenderableWidget(new CoreButton(
                    Component.literal("Open"),
                    b -> {
                        if (confirmBox != index) { confirmBox = index; return; }
                        if (waitingTicks > 0 || revealTicks > 0) return;
                        waitingTicks = 60;
                        action(20 + index);
                        confirmBox = -1;
                    },
                    layout.cardX(0) + keyXInset, keyY,
                    layout.cardWidth() - keyXInset - 6, keyHeight,
                    false, () -> confirmBox == index));
            buffs[i] = addRenderableWidget(new CoreButton(
                    Component.literal("Bless"),
                    b -> action(30 + index),
                    layout.cardX(1) + keyXInset, keyY,
                    layout.cardWidth() - keyXInset - 6, keyHeight,
                    false, () -> false));
        }
        updateControlState();
        updateBuildingReport();
    }

    private void selectPage(CoreCommandPage page) {
        if (tab == page) return;
        tab = page;
        updateBuildingReport();
        confirmBox = -1;
        intelDragging = false;
        setFocused(null); // Never leave keyboard focus on a now-hidden purchase action.
        updateNavigation();
        updateControlState();
        setFocused(pageButtons[tab.ordinal()]);
    }

    private CoreBuildingLayout buildingLayout() { return new CoreBuildingLayout(layout); }

    private void selectBuildingSection(BuildingSection section) {
        if (buildingSection == section) return;
        buildingSection = section;
        updateBuildingReport();
        setFocused(null);
        updateControlState();
        setFocused(buildingSections[section.ordinal()]);
    }

    private void selectDefense(DefenseBlueprint.Kind kind) {
        selectedDefense = kind;
        if (layout != null) planPage = kind.ordinal() / buildingLayout().plansPerPage();
        updateControlState();
    }

    private void movePlanPage(int direction) {
        var building = buildingLayout();
        int next = Math.max(0, Math.min(building.cataloguePages() - 1, planPage + direction));
        if (next == planPage) return;
        selectDefense(DefenseBlueprint.Kind.values()[next * building.plansPerPage()]);
        // A pager can become disabled at the end. Put focus on the selected visible plan.
        setFocused(defensePlans[selectedDefense.ordinal()]);
    }

    private void updateBuildingReport() {
        buildingReport.update(tab == CoreCommandPage.DEFENSES
                && buildingSection == BuildingSection.CONSTRUCTION, this::action);
    }

    private int constructionRowHeight() { return buildingLayout().reportRowHeight(); }
    private boolean projectReports() { return menu.construction().stream().anyMatch(job -> job.projectId() != null); }
    private int constructionRows() { return projectReports() ? 1 : buildingLayout().reportRows(); }
    private int constructionPages() { return Math.max(1, (menu.construction().size() + constructionRows() - 1) / constructionRows()); }
    private ConstructionReport.Job selectedConstruction() {
        int index = constructionPage * constructionRows();
        return index >= 0 && index < menu.construction().size() ? menu.construction().get(index) : null;
    }

    private void confirmProjectCancellation() {
        // Bind the last drawn job, not an index that a newer report could reorder under the pointer.
        var target = displayedCancellationTarget;
        if (minecraft == null || tab != CoreCommandPage.DEFENSES || buildingSection != BuildingSection.CONSTRUCTION
                || target == null || !target.cancelable() || cancellationCooldown > 0) return;
        int menuId = menu.containerId;
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                cancellationCooldown = 40;
                RaidNetwork.cancelPerimeterProject(menuId, target.projectId(), target.generation());
                updateControlState();
            }
        }, Component.literal("Cancel entire perimeter?"),
                Component.literal("All sections of this perimeter stop. Placed blocks stay. The commission and consumed materials are not refunded.")));
    }

    private void movePage(int direction) {
        var strip = layout.tabs(PAGES.length, tab.ordinal());
        selectPage(PAGES[strip.next(tab.ordinal(), direction)]);
    }

    private void updateNavigation() {
        var strip = layout.tabs(PAGES.length, tab.ordinal());
        for (int i = 0; i < PAGES.length; i++) {
            pageButtons[i].visible = strip.shows(i);
            pageButtons[i].setX(strip.tabX(i));
            pageButtons[i].setWidth(strip.tabWidth());
        }
        previousPage.visible = nextPage.visible = strip.overflow();
        previousPage.setX(strip.x());
        nextPage.setX(strip.x() + strip.width() - CoreTabStrip.ARROW_WIDTH);
    }

    private void selectIntelSection(int section) {
        if (section == intelSection) return;
        intelOffsets[intelSection] = intelOffset;
        intelSection = section;
        intelOffset = intelOffsets[section];
        intelDragging = false;
        intelMaxOffset = 0; // Recomputed on the next draw at this section's actual width.
    }

    private void filterIntel(String query) {
        intelQuery = query;
        java.util.Arrays.fill(intelOffsets, 0);
        intelOffset = intelMaxOffset = 0;
        intelDragging = false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (hasControlDown() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            movePage(hasShiftDown() ? -1 : 1);
            return true;
        }
        if (tab == CoreCommandPage.INTEL && intelSearch != null) {
            if (hasControlDown() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_F) {
                setFocused(intelSearch);
                return true;
            }
            // Inventory hotkeys (including E) must remain ordinary search text.
            if (intelSearch.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    && key != org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
                intelSearch.keyPressed(key, scan, modifiers);
                return true;
            }
        }
        if (tab == CoreCommandPage.INTEL && (intelSearch == null || !intelSearch.isFocused())) {
            int next = keyboardScroll(intelOffset, intelMaxOffset, Math.max(12, intelBodyH - 12), key);
            if (next >= 0) { intelOffset = next; intelDragging = false; return true; }
        }
        if (tab == CoreCommandPage.TREASURY && layout != null) {
            int rows = Math.max(1, (bankRosterHeight() - 26) / 12);
            int next = keyboardScroll(rosterOffset, Math.max(0, menu.members().size() - rows), rows, key);
            if (next >= 0) { rosterOffset = next; return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    static int keyboardScroll(int current, int maximum, int page, int key) {
        int target = switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> current - page;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> current + page;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> 0;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> maximum;
            default -> -1;
        };
        if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP && key != org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN
                && key != org.lwjgl.glfw.GLFW.GLFW_KEY_HOME && key != org.lwjgl.glfw.GLFW.GLFW_KEY_END) return -1;
        return Math.max(0, Math.min(Math.max(0, maximum), target));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (intelSearch != null && intelSearch.visible) intelSearch.tick();
        if (planRequestCooldown > 0) planRequestCooldown--;
        if (perimeterRequestCooldown > 0) perimeterRequestCooldown--;
        if (cancellationCooldown > 0) cancellationCooldown--;
        updateControlState();
        if (waitingTicks > 0) waitingTicks--;
        if (menu.lootSequence() != seenLoot) {
            seenLoot = menu.lootSequence();
            revealBox = menu.lootBox();
            revealed = menu.lootReward().copy();
            revealedTier = menu.lootTier();
            revealTicks = CoreLoot.OPEN_TICKS;
            waitingTicks = 0;
        }
        if (revealTicks > 0) {
            revealTicks--;
            if (tab == CoreCommandPage.LOOT && (revealTicks == 0
                    || revealTicks % (revealTicks > 20 ? 5 : 10) == 0)
                    && minecraft != null) {
                float pitch = revealTicks == 0
                        ? 1.2f
                        : 0.6f + (CoreLoot.OPEN_TICKS - revealTicks) * 0.01f;
                minecraft.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, pitch));
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);

        int logicalMouseX = layout.logicalX(mx);
        int logicalMouseY = layout.logicalY(my);
        g.pose().pushPose();
        g.pose().scale(layout.scale(), layout.scale(), 1.0F);

        super.render(g, logicalMouseX, logicalMouseY, partial);
        g.pose().popPose();
        // Tooltips stay at Minecraft's normal readable scale. Hover testing
        // still uses logical coordinates so tiny-window scaling cannot shift
        // the target away from the rendered card or button.
        drawTooltips(g, logicalMouseX, logicalMouseY, mx, my);
    }

    /**
     * Button state changes at game-tick frequency, not frame frequency. The
     * old render loop allocated dozens of Components every frame (often
     * 100+ times per second), even while every value was unchanged.
     */
    private void updateControlState() {
        if (layout == null || hire[0] == null) return;
        for (Button section : intelSections) section.visible = tab == CoreCommandPage.INTEL;
        intelSearch.visible = clearIntelSearch.visible = tab == CoreCommandPage.INTEL;
        clearIntelSearch.active = !intelQuery.isEmpty();
        civilianRecruit.visible = tab == CoreCommandPage.CIVILIANS;
        civilianRecruit.active = canAfford(CoreCivilians.PRICE) && menu.civilians() < CivilianLedger.LIMIT;
        civilianRecruit.setMessage(Component.literal(menu.civilians() >= CivilianLedger.LIMIT
                ? "Housing full · " + CivilianLedger.LIMIT + " residents"
                : canAfford(CoreCivilians.PRICE) ? "House a civilian · " + CoreCivilians.PRICE + "e"
                : "Need " + Math.max(0L, CoreCivilians.PRICE - availableFunds()) + "e in Treasury"));
        ((CoreButton) treasuryShortcut).setDetail(String.format(Locale.ROOT, "%,d", menu.bank()));
        for (int i = 0; i < 4; i++) {
            hire[i].visible = tab == CoreCommandPage.ARMY;
            hire[i].active = menu.role(i) >= 0 && menu.role(i) < CoreHiring.NAMES.length && menu.cost(i) >= 0
                    && !menu.sold(i) && menu.rotation() > 0 && canAfford(menu.cost(i));
            String actionLabel = menu.sold(i)
                    ? "Hired"
                    : menu.cost(i) < 0
                            ? "Unavailable"
                            : canAfford(menu.cost(i)) ? "Hire · " + menu.cost(i) + "e"
                            : "Need " + Math.max(0L, menu.cost(i) - availableFunds()) + "e";
            hire[i].setMessage(Component.literal(actionLabel));

            bank[i].visible = tab == CoreCommandPage.TREASURY;
            bank[i].active = i < 2 ? menu.emeralds() > 0 : menu.canWithdraw() && menu.bank() > 0;
        }
        for (int i = 0; i < siegeYard.length; i++) {
            siegeYard[i].visible = tab == CoreCommandPage.ARMY;
            siegeYard[i].active = SiegeYard.available() && canAfford(SiegeYard.PRICES[i]);
            String shortLabel = i == 0 ? "Catapult" : "Ballista";
            siegeYard[i].setMessage(Component.literal(
                    SiegeYard.available()
                            ? (layout.compact() ? shortLabel : shortLabel + " Kit")
                                    + "  ·  " + SiegeYard.PRICES[i] + "e"
                            : shortLabel + "  ·  unavailable"));
        }
        boolean buildingVisible = tab == CoreCommandPage.DEFENSES;
        boolean plansVisible = buildingVisible && buildingSection == BuildingSection.STRUCTURES;
        boolean jobsVisible = buildingVisible && buildingSection == BuildingSection.CONSTRUCTION;
        boolean perimeterVisible = buildingVisible && buildingSection == BuildingSection.PERIMETER;
        for (Button section : buildingSections) section.visible = buildingVisible;
        constructionPage = Math.max(0, Math.min(constructionPage, constructionPages() - 1));
        constructionPrevious.visible = constructionNext.visible = jobsVisible;
        constructionPrevious.active = constructionPage > 0;
        constructionNext.active = constructionPage + 1 < constructionPages();
        var selectedJob = selectedConstruction();
        boolean canCancel = jobsVisible && selectedJob != null && selectedJob.cancelable();
        constructionCancel.visible = canCancel;
        constructionCancel.active = canCancel && cancellationCooldown == 0;
        constructionCancel.setMessage(Component.literal(cancellationCooldown > 0 ? "Waiting for server…" : "Cancel entire perimeter"));
        if (!canCancel && getFocused() == constructionCancel) setFocused(buildingSections[BuildingSection.CONSTRUCTION.ordinal()]);
        var reportLayout = buildingLayout();
        boolean compactCancellation = canCancel && !reportLayout.splitReport();
        int navigationWidth = compactCancellation ? reportLayout.reportCancelNavigationWidth() : reportLayout.reportNavigationWidth();
        constructionPrevious.setWidth(navigationWidth);
        constructionNext.setWidth(navigationWidth);
        constructionNext.setX(reportLayout.x() + reportLayout.width() - navigationWidth);
        constructionPrevious.setMessage(Component.literal(compactCancellation ? "<" : "< Previous"));
        constructionNext.setMessage(Component.literal(compactCancellation ? ">" : "Next >"));
        int firstPlan = planPage * reportLayout.plansPerPage();
        for (int i = 0; i < defensePlans.length; i++)
            defensePlans[i].visible = plansVisible && i >= firstPlan && i < firstPlan + reportLayout.plansPerPage();
        previousPlans.visible = nextPlans.visible = plansVisible && reportLayout.pagedCatalogue();
        previousPlans.active = planPage > 0;
        nextPlans.active = planPage + 1 < reportLayout.cataloguePages();
        takeDefensePlan.visible = plansVisible;
        takeDefensePlan.active = planRequestCooldown == 0;
        takeDefensePlan.setMessage(Component.literal(planRequestCooldown > 0 ? "Requesting plan…"
                : buildingLayout().detailedCatalogue() ? "Take free plan" : "Free plan: " + selectedDefense.label));
        reviewPerimeter.setMessage(Component.literal(perimeterRequestCooldown > 0 ? "Requesting review…" : "Review in world"));
        reviewPerimeter.visible = perimeterVisible;
        // Reviewing is free, even when the Treasury cannot yet fund commission.
        reviewPerimeter.active = perimeterRequestCooldown == 0;
        for (Button material : fortifyButtons) material.visible = perimeterVisible;
        for (int i = 0; i < territoryBuffs.length; i++) {
            territoryBuffs[i].visible = tab == CoreCommandPage.TERRITORY;
            boolean owned = menu.hasTerritoryBuff(i);
            territoryBuffs[i].active = !owned && canAfford(TerritoryBuffs.PRICES[i]);
            long missing = canAfford(TerritoryBuffs.PRICES[i]) ? 0L : Math.max(0L, TerritoryBuffs.PRICES[i] - availableFunds());
            territoryBuffs[i].setMessage(Component.literal(
                    owned ? "Active"
                            : missing == 0L
                                    ? (layout.compact() ? "Buy" : "Enact")
                                            + "  ·  " + TerritoryBuffs.PRICES[i] + "e"
                                    : "Need  ·  " + missing + "e"));
        }
        for (int i = 0; i < 3; i++) {
            boxes[i].visible = buffs[i].visible = tab == CoreCommandPage.LOOT;
            boxes[i].active = waitingTicks == 0 && revealTicks == 0
                    && canAfford(CoreLoot.price(i));
            boxes[i].setMessage(Component.literal(
                    waitingTicks > 0 ? "Waiting..."
                            : revealTicks > 0 ? "Unsealing..."
                            : !canAfford(CoreLoot.price(i)) ? "Need " + Math.max(0L, CoreLoot.price(i) - availableFunds()) + "e"
                            : (confirmBox == i ? "Confirm  ·  " : "Open  ·  ")
                                    + CoreLoot.price(i) + "e"));

            boolean active = minecraft != null && minecraft.player != null
                    && minecraft.player.hasEffect(CoreBuffs.effect(i));
            buffs[i].active = !active && canAfford(CoreBuffs.PRICES[i]);
            buffs[i].setMessage(Component.literal(
                    active ? "Blessing active" : !canAfford(CoreBuffs.PRICES[i])
                            ? "Need " + Math.max(0L, CoreBuffs.PRICES[i] - availableFunds()) + "e"
                            : "Bless  ·  " + CoreBuffs.PRICES[i] + "e"));
        }
        ensureVisibleFocus();
    }

    static boolean visibleHover(Button button, double x, double y) {
        return button.visible && button.isMouseOver(x, y);
    }

    private void drawTooltips(GuiGraphics g, int mx, int my,
                              int tooltipX, int tooltipY) {
        if (treasuryShortcut.isMouseOver(mx, my)) {
            tooltip(g, String.format(Locale.ROOT, "Faction Treasury: %,d emeralds. Purchases use this balance. Click to deposit or withdraw.", menu.bank()), tooltipX, tooltipY);
            return;
        }
        if (over(mx, my, layout.purseX(), layout.y() + 6, layout.purseWidth(), 24)) {
            tooltip(g, String.format(Locale.ROOT, "Your purse: %,d emeralds. Deposit into the faction Treasury before purchasing.", menu.emeralds()), tooltipX, tooltipY);
            return;
        }
        if (visibleHover(previousPage, mx, my) || visibleHover(nextPage, mx, my)) {
            tooltip(g, "Previous / next page. Ctrl+Tab cycles pages; Ctrl+Shift+Tab goes back.", tooltipX, tooltipY);
            return;
        }
        for (int i = 0; i < PAGES.length; i++) {
            if (visibleHover(pageButtons[i], mx, my)) {
                tooltip(g, PAGES[i].title() + " | " + PAGES[i].description()
                        + " | Ctrl+Tab or scroll over the tabs to switch.", tooltipX, tooltipY);
                return;
            }
        }
        if (layout.pageHeaderHeight() > 0 && over(mx, my, layout.x() + 10,
                layout.pageHeaderY(), layout.width() - 20, layout.pageHeaderHeight())) {
            tooltip(g, pageContext(), tooltipX, tooltipY);
            return;
        }
        if (tab == CoreCommandPage.ARMY) {
            for (int i = 0; i < 4; i++) {
                if (over(mx, my, layout.cardX(i), layout.cardY(i),
                        layout.cardWidth(), layout.cardHeight()) && menu.role(i) >= 0) {
                    int role = menu.role(i);
                    String purchaseState = menu.sold(i)
                            ? "Already hired this rotation."
                            : menu.cost(i) < 0
                                    ? "This offer is unavailable."
                                    : canAfford(menu.cost(i))
                                            ? "Ready to hire."
                                            : "Need " + emeralds(menu.cost(i) - availableFunds()) + " more.";
                    String info = CoreHiring.NAMES[role]
                            + "  |  " + CoreHiring.rarity(role)
                            + "  |  " + (i == 3 ? HeroTraits.description(role) : roleBlurb(role))
                            + "  |  " + kitDescriptor(role)
                            + "  |  " + purchaseState
                            + "  |  Shared faction offer; refreshes every 15 minutes.";
                    tooltip(g, info, tooltipX, tooltipY);
                }
            }
            for (int i = 0; i < siegeYard.length; i++) {
                if (siegeYard[i].isMouseOver(mx, my)) {
                    String info;
                    if (!SiegeYard.available()) {
                        info = "Requires both Villager Recruits and Siege Weapons.";
                    } else {
                        SiegeIntegration.Footprint footprint =
                                SiegeIntegration.footprintOf(SiegeYard.TYPES[i]);
                        String area = SiegeYard.deploymentAreaGuidance(footprint);
                        if (!canAfford(SiegeYard.PRICES[i])) {
                            info = "You need " + emeralds(SiegeYard.PRICES[i] - availableFunds())
                                    + " more (faction Treasury). Deployment requires a clear, solid, flat "
                                    + area + ".";
                        } else {
                            info = "Buy the kit now, then right-click the top of a clear, solid, flat "
                                    + area + " to deploy your " + SiegeYard.LABELS[i]
                                    + ". A failed placement keeps the kit.";
                        }
                    }
                    tooltip(g, info, tooltipX, tooltipY);
                }
            }
        }
        if (feedbackButton != null && feedbackButton.isMouseOver(mx, my)) {
            tooltip(g, "Open the Siege Overhaul CurseForge comments page to share feedback or report a problem.",
                    tooltipX, tooltipY);
        }
        if (tab == CoreCommandPage.LOOT) {
            for (int i = 0; i < 3; i++) {
                if (over(mx, my, layout.cardX(0), layout.marketY(i),
                        layout.cardWidth(), layout.marketHeight())) {
                    String t = revealBox == i && revealTicks == 0 && !revealed.isEmpty()
                            ? CoreLoot.rarity(revealedTier) + "  |  " + revealed.getCount()
                                    + "x " + revealed.getHoverName().getString()
                            : "One mystery reward  |  " + CoreLoot.odds();
                    tooltip(g, t, tooltipX, tooltipY);
                }
                if (over(mx, my, layout.cardX(1), layout.marketY(i),
                        layout.cardWidth(), layout.marketHeight())) {
                    tooltip(g, CoreBuffs.DETAILS[i]
                            + " for 5 minutes. Uses the faction Treasury;"
                            + " existing effects are preserved.", tooltipX, tooltipY);
                }
            }
        }
        if (tab == CoreCommandPage.TREASURY) {
            int bankY = layout.contentY();
            if (over(mx, my, layout.x() + 10, bankY,
                    layout.width() - 20, 46)) {
                long dailyInterest = (long) menu.bank() * menu.interestRate() / 10000L;
                tooltip(g, String.format(Locale.ROOT,
                        "Faction treasury: %,d emeralds  |  Next wave: +%,d  |  Interest: +%,d in %s (%.2f%%/day, in-game time)  |  Deposit emeralds here before purchasing.",
                        menu.bank(), menu.nextReward(), dailyInterest,
                        formatInterestCountdown(menu.ticksUntilInterest()),
                        menu.interestRate() / 100.0), tooltipX, tooltipY);
            }
            for (int i = 0; i < bank.length; i++) {
                if (bank[i].isMouseOver(mx, my)) {
                    boolean deposit = i < 2;
                    int amount = i % 2 == 0 ? 8 : 64;
                    String reason = deposit && menu.emeralds() == 0 ? "Your purse is empty. "
                            : !deposit && !menu.canWithdraw() ? "Your faction role cannot withdraw Treasury funds. "
                            : !deposit && menu.bank() == 0 ? "The faction Treasury is empty. " : "";
                    tooltip(g, reason + (deposit ? "Deposit up to " : "Withdraw up to ") + amount
                            + " emeralds " + (deposit
                                    ? "from your purse into the shared faction Treasury."
                                    : "from the shared faction Treasury into your purse."),
                            tooltipX, tooltipY);
                }
            }
        }
        if (tab == CoreCommandPage.CIVILIANS && over(mx, my, layout.x() + 10,
                layout.contentY(), layout.width() - 20, layout.contentBottom() - layout.contentY())) {
            String reason = menu.civilians() >= CivilianLedger.LIMIT ? "Your faction has reached its resident limit. "
                    : !canAfford(CoreCivilians.PRICE) ? "Deposit " + emeralds(CoreCivilians.PRICE - availableFunds())
                            + " in the Treasury to house another resident. " : "";
            tooltip(g, reason + "Housing costs " + CoreCivilians.PRICE + " Treasury emeralds. Give residents beds, food and workstations. "
                    + "Each living resident pays one emerald per full in-game day. Taxes pause when stranded or while the core is occupied.",
                    tooltipX, tooltipY);
            return;
        }
        if (tab == CoreCommandPage.DEFENSES) drawBuildingTooltips(g, mx, my, tooltipX, tooltipY);
        if (tab == CoreCommandPage.TERRITORY) {
            int cols = 2;
            int gridTop = layout.contentY();
            int cellW = layout.territoryCardWidth();
            int cellH = layout.territoryCardHeight();
            for (int i = 0; i < TerritoryBuffs.COUNT; i++) {
                int cx = layout.x() + 10 + (i % cols) * (cellW + 8);
                int cy = gridTop + (i / cols) * (cellH + 8);
                if (over(mx, my, cx, cy, cellW, cellH)) {
                    long missing = canAfford(TerritoryBuffs.PRICES[i]) ? 0L : Math.max(0L, TerritoryBuffs.PRICES[i] - availableFunds());
                    String state = menu.hasTerritoryBuff(i)
                            ? "Already active for the whole faction."
                            : missing == 0L
                                    ? "One-time purchase. Paid from the faction Treasury only."
                                    : "Need " + emeralds(missing) + " more (faction Treasury).";
                    tooltip(g, TerritoryBuffs.LABELS[i] + "  |  "
                            + TerritoryBuffs.DESCRIPTIONS[i] + "  |  " + state,
                            tooltipX, tooltipY);
                }
            }

        }
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void tooltip(GuiGraphics g, String text, int x, int y) {
        int maxByViewport = Math.max(1, width - 24);
        int maxByPanel = layout == null ? maxByViewport : Math.max(1, layout.width() - 24);
        int wrapWidth = Math.max(1, Math.min(300, Math.min(maxByViewport, maxByPanel)));
        g.renderTooltip(font, wrapped(text, wrapWidth), x, y);
    }

    private static String emeralds(long amount) {
        return String.format(Locale.ROOT, "%,d emeralds", Math.max(0L, amount));
    }

    private void text(GuiGraphics g, String text, int x, int y, int width, int color) {
        int available = Math.max(1, width);
        TextMeasureKey key = new TextMeasureKey(text, available);
        String fitted = fittedText.get(key);
        if (fitted == null) {
            fitted = font.plainSubstrByWidth(text, available);
            if (!fitted.equals(text) && available > font.width("…")) {
                fitted = font.plainSubstrByWidth(text,
                        available - font.width("…")) + "…";
            }
            fittedText.put(key, fitted);
        }
        g.drawString(font, fitted, x, y, color, false);
    }

    /** Draw a right-aligned, text-only state badge and return its width. */
    private int drawBadge(GuiGraphics g, String label, int right, int y, int accent) {
        int badgeWidth = font.width(label) + 10;
        int left = right - badgeWidth;
        CommandFrame.badge(g, left, y, badgeWidth, 13, accent);
        text(g, label, left + 6, y + 3, badgeWidth - 8, accent);
        return badgeWidth;
    }

    private List<FormattedCharSequence> wrapped(String text, int width) {
        int available = Math.max(1, width);
        TextMeasureKey key = new TextMeasureKey(text, available);
        List<FormattedCharSequence> cached = wrappedText.get(key);
        if (cached != null) return cached;
        List<FormattedCharSequence> lines = List.copyOf(
                font.split(Component.literal(text), available));
        wrappedText.put(key, lines);
        return lines;
    }

    private long availableFunds() {
        return menu.bank();
    }

    private boolean canAfford(int price) {
        boolean creative = minecraft != null && minecraft.player != null
                && minecraft.player.getAbilities().instabuild;
        return creative || availableFunds() >= price;
    }

    private int hirePortraitSize() { return layout.hirePortraitSize(); }

    @Override
    protected void renderLabels(GuiGraphics g, int x, int y) {}

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = layout.x(), y = layout.y(), w = layout.width(), h = layout.height();

        // Quiet dark surfaces keep the world visible around a single thin frame.
        CommandFrame.window(g, x, y, w, h);
        // Header banner strip that sits behind the crown, title and treasury chip.
        CommandFrame.header(g, x, y, w, 26);

        // Hanging crest banner on the left of the header (like the reference).
        drawCrestBanner(g, x + 5, y + 7);

        // Keep the native Minecraft font and crest, with one crisp title instead of layered shadows.
        String title = layout.compact() ? tab.label().toUpperCase(Locale.ROOT) : "SIEGE COMMAND";
        text(g, title, x + 42, y + 12, layout.headerTitleWidth(), CommandPalette.ACCENT_GOLD);
        text(g, menu.factionName(),
                x + 42, y + 22, layout.headerTitleWidth(), CommandPalette.TEXT_MUTED);

        // Both balances remain visible on every page. The Treasury chip is a real
        // keyboard-accessible navigation button, not an unlabeled decorative icon.
        int chipW = layout.purseWidth(), chipX = layout.purseX();
        CommandFrame.chip(g, chipX, y + 6, chipW, 24, CommandPalette.ACCENT_STEEL);
        text(g, "PURSE", chipX + 6, y + 9, chipW - 12, CommandPalette.TEXT_MUTED);
        text(g, String.format(Locale.ROOT, "%,d", menu.emeralds()), chipX + 6, y + 20,
                chipW - 12, CommandPalette.TEXT);

        // Active-siege ribbon under the header.
        drawSiegeRibbon(g, x, y, w);

        // Tab body.
        drawPageHeader(g);

        if (tab == CoreCommandPage.ARMY) {
            for (int i = 0; i < 4; i++) drawHire(g, i, mx, my);
        } else if (tab == CoreCommandPage.LOOT) {
            for (int i = 0; i < 3; i++) {
                drawLoot(g, i, mx, my);
                drawBuff(g, i, mx, my);
            }
            drawLootReserve(g);
        } else if (tab == CoreCommandPage.TREASURY) {
            drawFaction(g);
        } else if (tab == CoreCommandPage.TERRITORY) {
            drawTerritory(g, mx, my);
        } else if (tab == CoreCommandPage.DEFENSES) {
            drawDefenses(g, mx, my);
        } else if (tab == CoreCommandPage.CIVILIANS) {
            drawCivilians(g, mx, my);
        } else if (tab == CoreCommandPage.INTEL) {
            drawIntel(g, mx, my);
        }

        // Footer stats strip: faction size + contextual tab hint.
        drawFooterStrip(g, x, layout.footerY(), w);
    }

    /** Modern two-line page identity shared by every roomy tab. */
    private void drawPageHeader(GuiGraphics g) {
        if (layout.pageHeaderHeight() == 0 || tab == CoreCommandPage.DEFENSES) return;
        int x = layout.x() + 10;
        int y = layout.pageHeaderY();
        int w = layout.width() - 20;
        int accent = pageAccent();

        g.fill(x, y, x + w, y + CoreHireLayout.PAGE_HEADER_HEIGHT,
                0x8a0b111d);
        g.fill(x, y, x + 2, y + CoreHireLayout.PAGE_HEADER_HEIGHT, accent);
        g.fill(x + 2, y + CoreHireLayout.PAGE_HEADER_HEIGHT - 1,
                x + w, y + CoreHireLayout.PAGE_HEADER_HEIGHT,
                CommandPalette.DIVIDER);

        String title = tab.title();
        String subtitle = tab.description();
        String metric = pageMetric();
        int metricWidth = Math.min(w / 3, font.width(metric) + 4);
        text(g, title, x + 8, y + 3,
                w - metricWidth - 28, accent);
        text(g, metric, x + w - metricWidth - 7, y + 3,
                metricWidth, CommandPalette.TEXT);
        text(g, subtitle, x + 8, y + 14, w - 16, CommandPalette.TEXT_MUTED);
    }

    private int pageAccent() {
        return switch (tab) {
            case LOOT -> CommandPalette.ACCENT_ARCANE;
            case TREASURY -> CommandPalette.ACCENT_EMERALD;
            case TERRITORY, DEFENSES -> CommandPalette.ACCENT_TEAL;
            case INTEL -> CommandPalette.ACCENT_STEEL;
            default -> CommandPalette.ACCENT_GOLD;
        };
    }

    private String pageMetric() {
        return switch (tab) {
            case ARMY -> String.format(Locale.ROOT, "Refresh %d:%02d",
                    menu.seconds() / 60, menu.seconds() % 60);
            case CIVILIANS -> menu.civilians()+" / 64 residents";
            case LOOT -> String.format(Locale.ROOT, "Treasury %,de", menu.bank());
            case TREASURY -> String.format(Locale.ROOT, "Balance %,de", menu.bank());
            case DEFENSES -> buildingSection.label;
            case TERRITORY -> ownedTerritoryBuffs() + "/" + TerritoryBuffs.COUNT + " active";
            case INTEL -> switch (intelSection) {
                case 0 -> "Unit archive";
                case 1 -> "Host archive";
                default -> "Field doctrine";
            };
            default -> "";
        };
    }

    private String pageContext() {
        return switch (tab) {
            case ARMY -> "Four faction-wide offers · purchases deploy from the shared Treasury";
            case LOOT -> "Rewards stay concealed until opened · purchases use the shared Treasury";
            case TREASURY -> "Every transaction is faction-wide and recorded in recent activity";
            case DEFENSES -> "Free review and plans · commission from the Treasury · supply blocks through Workers storage";
            case TERRITORY -> "Permanent decrees affect every faction member · construction lives in Building";
            case INTEL -> "Ctrl+F: search · Page Up / Down: read · Home / End: jump";
            case CIVILIANS -> "Each living resident pays one emerald per full in-game day; taxes pause if stranded or the core is occupied";
            default -> "";
        };
    }

    private int ownedTerritoryBuffs() {
        int owned = 0;
        for (int i = 0; i < TerritoryBuffs.COUNT; i++) {
            if (menu.hasTerritoryBuff(i)) owned++;
        }
        return owned;
    }

    /** Textured hanging crest banner rendered from the HUD atlas. */
    private void drawCrestBanner(GuiGraphics g, int x, int y) {
        HudAtlas.blit(g, HudAtlas.CREST_BANNER, x, y - 4);
    }

    /**
     * Bottom strip with the faction size chip on the left, contextual tab
     * hint in the middle, and refresh timer / interest on the right.
     */
    private void drawFooterStrip(GuiGraphics g, int x, int y, int w) {
        // Footer is its own fixed band. Keeping all controls inside this band
        // prevents the Army deployment row from colliding at large GUI scales.
        g.fill(x + 4, y, x + w - 4, y + CoreHireLayout.FOOTER_HEIGHT,
                CommandPalette.PANEL_BOTTOM);
        g.fill(x + 6, y, x + w - 6, y + 1, CommandPalette.DIVIDER);

        int feedbackW = layout.feedbackWidth();
        int feedbackLeft = x + w - feedbackW - 8;

        text(g, footerHint(tab, layout.compact(), menu.seconds()), x + 10, y + 4,
                feedbackLeft - x - 18, CommandPalette.TEXT_MUTED);
    }

    static String footerHint(CoreCommandPage page, boolean compact, int seconds) {
        String count = (page.ordinal() + 1) + "/" + PAGES.length;
        if (compact && page == CoreCommandPage.ARMY) return count + " · Refresh "
                + String.format(Locale.ROOT, "%d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60)
                + " · Ctrl+Tab";
        return compact ? count + " · Esc: close · Ctrl+Tab"
                : "Page " + (page.ordinal() + 1) + " / " + PAGES.length
                    + " · Esc: close · Ctrl+Tab: next · Ctrl+Shift+Tab: previous";
    }

    private void ensureVisibleFocus() {
        var focused = getFocused();
        if (focused == null || focused instanceof net.minecraft.client.gui.components.AbstractWidget widget
                && children().contains(widget) && widget.visible && widget.active) return;
        if (tab == CoreCommandPage.DEFENSES && buildingSection == BuildingSection.STRUCTURES
                && defensePlans[selectedDefense.ordinal()] != null && defensePlans[selectedDefense.ordinal()].visible)
            setFocused(defensePlans[selectedDefense.ordinal()]);
        else if (pageButtons[tab.ordinal()] != null) setFocused(pageButtons[tab.ordinal()]);
    }

    /**
     * Intel tab: three sub-sections (Units, Enemy Lore, How to Play) that
     * bake in the old Warlord's Codex content so the player no longer needs
     * to spawn a book.
     */
    private void drawIntel(GuiGraphics g, int mouseX, int mouseY) {
        int x = layout.x() + 10, y = layout.contentY();
        int w = layout.width() - 20;
        int h = layout.height() - (y - layout.y()) - 22;

        // Body panel
        int bodyY = y + 46;
        int bodyH = h - 48;
        CommandFrame.card(g, x, bodyY, w, bodyH, CommandPalette.ACCENT_ARCANE);

        // Reserve an 8px scrollbar gutter on the right so content never draws
        // under the thumb. Content clip is narrower than the panel.
        int scrollGutter = 10;
        int contentRight = x + w - scrollGutter;

        // Use scissor so long content clips at the panel edges.
        enableLayoutScissor(g, x + 2, bodyY + 2,
                contentRight, bodyY + bodyH - 2);
        int cursorY = bodyY + 8 - intelOffset;
        int textX = x + 10;
        int textW = w - 20 - scrollGutter;

        int drawn = switch (intelSection) {
            case 0 -> drawUnitsSection(g, textX, cursorY, textW);
            case 1 -> drawLoreSection(g, textX, cursorY, textW);
            default -> drawHowToPlaySection(g, textX, cursorY, textW);
        };
        if (drawn == 0) {
            text(g, "No matches. Try another word or Clear.", textX, bodyY + 10,
                    textW, CommandPalette.TEXT_MUTED);
        }
        g.disableScissor();

        // Clamp scroll so we can't drag past the end.
        int maxOffset = Math.max(0, drawn - bodyH + 16);
        if (intelOffset > maxOffset) intelOffset = maxOffset;

        // Cache geometry for mouseClicked / mouseDragged.
        intelBodyX = x;
        intelBodyY = bodyY;
        intelBodyW = w;
        intelBodyH = bodyH;
        intelMaxOffset = maxOffset;

        // === Scrollbar ===
        // Track sits in the right gutter, inset a couple of pixels so it
        // reads as separate from the card border.
        int trackX = x + w - 8;
        int trackY = bodyY + 4;
        int trackW = 4;
        int trackH = bodyH - 8;
        g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x66000000);
        g.fill(trackX, trackY, trackX + 1, trackY + trackH, CommandPalette.BEVEL_DARK);
        g.fill(trackX + trackW - 1, trackY, trackX + trackW, trackY + trackH, CommandPalette.BEVEL_DARK);
        if (drawn > bodyH) {
            // Thumb sized proportional to visible / total, min 16px so it's clickable.
            int thumbH = Math.max(16, (int) ((long) trackH * bodyH / Math.max(1, drawn)));
            int thumbTravel = trackH - thumbH;
            int thumbY = trackY + (maxOffset == 0 ? 0
                    : (int) ((long) thumbTravel * intelOffset / maxOffset));
            int thumbColor = intelDragging
                    ? CommandPalette.ACCENT_GOLD
                    : CommandPalette.ACCENT_ARCANE;
            g.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH, thumbColor);
            g.fill(trackX, thumbY, trackX + trackW, thumbY + 1, CommandPalette.ACCENT_GOLD);
            g.fill(trackX, thumbY + thumbH - 1, trackX + trackW, thumbY + thumbH,
                    CommandPalette.BEVEL_DARK);
        } else {
            // Content fits: draw an inactive marker so the gutter still reads
            // as a scrollbar (empty track alone looks like a UI bug).
            g.fill(trackX, trackY, trackX + trackW, trackY + Math.min(16, trackH),
                    0x33ffffff);
        }
    }

    private boolean archiveCardVisible(int y, int height) {
        return y + height > layout.contentY() + 48 && y < layout.contentBottom();
    }

    private int drawUnitsSection(GuiGraphics g, int x, int startY, int w) {
        int y = startY;
        for (var entry : com.devfarinsky.siegeoverhaul.client.codex.UnitCodex.ENTRIES) {
            if (!CoreIntelSearch.matches(intelQuery, entry.name(), entry.tagline(), entry.stats(),
                    entry.behavior(), entry.counter(), entry.drops(), entry.availability())) continue;
            int innerW = Math.max(1, w - 16);
            int behaviorH = wrapped(entry.behavior(), innerW).size() * 10;
            int counterH = wrapped("Counter: " + entry.counter(), innerW).size() * 10;
            int dropsH = wrapped("Drops: " + entry.drops(), innerW).size() * 10;
            int cardH = 12 + 12 + 10 + 10 + behaviorH + counterH + dropsH + 10;
            if (!archiveCardVisible(y, cardH)) { y += cardH + 6; continue; }
            CommandFrame.card(g, x, y, w, cardH, CommandPalette.ACCENT_STEEL);
            int tx = x + 8;
            int ty = y + 6;
            text(g, entry.name(), tx, ty, innerW, CommandPalette.ACCENT_GOLD); ty += 12;
            text(g, entry.tagline(), tx, ty, innerW, CommandPalette.TEXT); ty += 10;
            text(g, entry.stats(), tx, ty, innerW, CommandPalette.ACCENT_TEAL); ty += 10;
            ty += drawWrapped(g, entry.behavior(), tx, ty, innerW, CommandPalette.TEXT_MUTED);
            ty += drawWrapped(g, "Counter: " + entry.counter(), tx, ty, innerW, CommandPalette.TEXT);
            ty += drawWrapped(g, "Drops: " + entry.drops(), tx, ty, innerW, CommandPalette.ACCENT_EMERALD);
            text(g, entry.availability(), tx, ty, innerW, CommandPalette.TEXT_DIM);
            y += cardH + 6;
        }
        return y - startY;
    }

    private int drawLoreSection(GuiGraphics g, int x, int startY, int w) {
        int y = startY;
        for (var entry : com.devfarinsky.siegeoverhaul.client.codex.FactionLore.all().entrySet()) {
            if (!CoreIntelSearch.matches(intelQuery, entry.getKey().replace('_', ' '),
                    String.join(" ", entry.getValue()))) continue;
            String name = entry.getKey().replace('_', ' ');
            // Simple title case.
            StringBuilder title = new StringBuilder(name.length());
            boolean cap = true;
            for (char c : name.toCharArray()) {
                title.append(cap ? Character.toUpperCase(c) : c);
                cap = c == ' ';
            }
            int innerW = Math.max(1, w - 16);
            int bodyH = 0;
            for (String line : entry.getValue()) {
                bodyH += wrapped(line, innerW).size() * 10;
            }
            int cardH = 12 + 12 + bodyH;
            if (!archiveCardVisible(y, cardH)) { y += cardH + 6; continue; }
            CommandFrame.card(g, x, y, w, cardH, CommandPalette.ACCENT_ARCANE);
            int tx = x + 8;
            int ty = y + 6;
            text(g, title.toString(), tx, ty, innerW, CommandPalette.ACCENT_GOLD);
            ty += 12;
            for (String line : entry.getValue()) {
                ty += drawWrapped(g, line, tx, ty, innerW, CommandPalette.TEXT_MUTED);
            }
            y += cardH + 6;
        }
        return y - startY;
    }

    private int drawHowToPlaySection(GuiGraphics g, int x, int startY, int w) {
        int y = startY;
        for (var tip : com.devfarinsky.siegeoverhaul.client.codex.DefensePlaybook.TIPS) {
            if (!CoreIntelSearch.matches(intelQuery, tip.tag(), tip.title(), tip.body())) continue;
            int innerW = Math.max(1, w - 16);
            int bodyH = wrapped(tip.body(), innerW).size() * 10;
            int cardH = 12 + 12 + bodyH;
            if (!archiveCardVisible(y, cardH)) { y += cardH + 6; continue; }
            CommandFrame.card(g, x, y, w, cardH, CommandPalette.ACCENT_TEAL);
            int tx = x + 8;
            int ty = y + 6;
            text(g, tip.tag().toUpperCase(Locale.ROOT), tx, ty,
                    Math.max(1, Math.min(76, innerW / 3) - 6), CommandPalette.ACCENT_TEAL);
            text(g, tip.title(), tx + Math.min(76, innerW / 3), ty,
                    Math.max(1, innerW - Math.min(76, innerW / 3)),
                    CommandPalette.ACCENT_GOLD);
            ty += 12;
            drawWrapped(g, tip.body(), tx, ty, innerW, CommandPalette.TEXT_MUTED);
            y += cardH + 6;
        }
        return y - startY;
    }

    /** Word-wrap helper. Returns the total height consumed. */
    private int drawWrapped(GuiGraphics g, String s, int x, int y, int w, int colour) {
        var lines = wrapped(s, w);
        int used = 0;
        for (var line : lines) {
            g.drawString(font, line, x, y + used, colour, false);
            used += 10;
        }
        return used;
    }

    /** GuiGraphics scissor coordinates do not inherit the pose-stack scale. */
    private void enableLayoutScissor(GuiGraphics g, int left, int top,
                                     int right, int bottom) {
        float scale = layout.scale();
        g.enableScissor(
                (int) Math.floor(left * scale),
                (int) Math.floor(top * scale),
                (int) Math.ceil(right * scale),
                (int) Math.ceil(bottom * scale));
    }

    private void drawBuildingTooltips(GuiGraphics g, int mx, int my, int tooltipX, int tooltipY) {
        var building = buildingLayout();
        for (BuildingSection section : BuildingSection.values()) {
            if (visibleHover(buildingSections[section.ordinal()], mx, my)) {
                String detail = switch (section) {
                    case PERIMETER -> "Choose a material, then review a free perimeter plan in world. Confirm separately to commission.";
                    case STRUCTURES -> "All six existing plans. Select a structure, take its free plan, then preview and confirm in your claim.";
                    case CONSTRUCTION -> "Your perimeter projects and loaded construction within 128 blocks, refreshed every two seconds. Unloaded sections remain unknown until checked. Recently completed or canceled projects stay visible for a while.";
                };
                tooltip(g, section.label + " | " + detail, tooltipX, tooltipY);
                return;
            }
        }
        if (buildingSection == BuildingSection.PERIMETER) {
            for (int i = 0; i < fortifyButtons.length; i++) {
                if (visibleHover(fortifyButtons[i], mx, my)) {
                    tooltip(g, TerritoryFortification.material(i).label()
                            + " perimeter palette. Selection and preview are free. Commission: "
                            + TerritoryFortification.PRICE + " faction Treasury emeralds plus supplied blocks.", tooltipX, tooltipY);
                    return;
                }
            }
            if (visibleHover(reviewPerimeter, mx, my)) {
                tooltip(g, "Receive a free perimeter plan and hold it to review in world. Commission only when confirmed. "
                        + "Needs an owned Workers builder and Builder-enabled storage with the exact blocks. "
                        + (canAfford(TerritoryFortification.PRICE) ? "Treasury funds are ready."
                        : "Review is free; commission needs " + emeralds(TerritoryFortification.PRICE - availableFunds())
                        + " more in the faction Treasury."), tooltipX, tooltipY);
            }
        } else if (buildingSection == BuildingSection.STRUCTURES) {
            for (DefenseBlueprint.Kind kind : DefenseBlueprint.Kind.values()) {
                if (visibleHover(defensePlans[kind.ordinal()], mx, my)) {
                    tooltip(g, defenseTooltip(kind), tooltipX, tooltipY);
                    return;
                }
            }
            if (visibleHover(takeDefensePlan, mx, my)) tooltip(g, defenseTooltip(selectedDefense), tooltipX, tooltipY);
        } else {
            if (visibleHover(constructionCancel, mx, my)) {
                tooltip(g, "Cancel the displayed whole perimeter, including all remaining sections. Placed blocks stay; no commission or consumed-material refund.", tooltipX, tooltipY);
                return;
            }
            int first = constructionPage * constructionRows();
            for (int row = 0; row < constructionRows() && first + row < menu.construction().size(); row++) {
                if (over(mx, my, building.x(), building.reportRowY(row), building.width(),
                        projectReports() ? building.reportProjectHeight() : constructionRowHeight() - 4)) {
                    var job = menu.construction().get(first + row);
                    tooltip(g, job.label() + " | " + job.progressText() + " | " + job.location()
                            + " | " + job.activity() + (job.sectionText().isBlank() ? "" : " | " + job.sectionText()) + " | Requested now: "
                            + (job.supplies().isBlank() ? "No active requests reported" : job.supplies()), tooltipX, tooltipY);
                    return;
                }
            }
        }
    }

    private String defenseTooltip(DefenseBlueprint.Kind kind) {
        return kind.label + " | " + kind.description + " | " + kind.dimensions()
                + " | " + kind.price + " Treasury emeralds on placement + "
                + DefenseBlueprint.create(kind, net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.SOUTH).materials()
                + ". Plan collection and preview are free. Use the same anchor again to pay and build. "
                + "Your idle builder must be within 16 blocks of the site; supply a Workers storage area with Builders enabled.";
    }

    private void drawConstruction(GuiGraphics g) {
        var building = buildingLayout();
        var jobs = menu.construction();
        constructionPage = Math.max(0, Math.min(constructionPage, constructionPages() - 1));
        displayedCancellationTarget = selectedConstruction();
        text(g, (projectReports() ? "Perimeters + nearby jobs · 2s · " : "Nearby · 128 blocks · refresh 2s · ")
                        + (constructionPage + 1) + "/" + constructionPages(),
                building.x() + 2, building.reportHeaderY() + 1, building.width() - 4, CommandPalette.TEXT_MUTED);
        if (jobs.isEmpty()) {
            CommandFrame.surface(g, building.x(), building.reportY(), building.width(),
                    building.actionY() - building.reportY() - 6);
            drawWrappedText(g, menu.constructionLoaded()
                            ? "No projects or loaded jobs nearby. Move within 128 blocks of your builder or build site."
                            : "Loading your construction...", building.x() + 10, building.reportY() + 10,
                    building.width() - 20, Math.max(1, (building.actionY() - building.reportY() - 20) / 10),
                    CommandPalette.TEXT_MUTED);
            return;
        }
        int first = constructionPage * constructionRows();
        for (int row = 0; row < constructionRows() && first + row < jobs.size(); row++) {
            var job = jobs.get(first + row);
            int x = building.x(), y = building.reportRowY(row);
            int w = building.reportMainWidth(), h = projectReports() ? building.reportProjectHeight() : constructionRowHeight() - 4;
            if (job.projectId() != null) {
                drawPerimeterProject(g, job, first + row, jobs.size(), x, y, w, h);
                continue;
            }
            CommandFrame.surface(g, x, y, w, h);
            text(g, (first + row + 1) + "/" + jobs.size() + "  " + job.label(),
                    x + 10, y + 7, w - 20, CommandPalette.TEXT);
            if (building.splitReport()) {
                text(g, "BUILD PROGRESS", x + 10, y + 26, w - 20, CommandPalette.TEXT_DIM);
                drawWrappedText(g, job.progressText(), x + 10, y + 40, w - 20, 2, CommandPalette.ACCENT_TEAL);
                text(g, job.location(), x + 10, y + 72, w - 20, CommandPalette.TEXT_MUTED);
                int detailX = building.reportDetailX(), detailW = building.reportDetailWidth();
                CommandFrame.surface(g, detailX, y, detailW, h);
                text(g, "BUILDER STATUS", detailX + 10, y + 7, detailW - 20, CommandPalette.TEXT_DIM);
                drawWrappedText(g, job.activity(), detailX + 10, y + 22, detailW - 20, 3, CommandPalette.TEXT);
                text(g, "NEEDED NOW", detailX + 10, y + 59, detailW - 20, CommandPalette.TEXT_DIM);
                drawWrappedText(g, job.supplies().isBlank() ? "No active requests reported" : job.supplies(),
                        detailX + 10, y + 74, detailW - 20, 2, CommandPalette.ACCENT_GOLD);
            } else {
                text(g, job.progressText(), x + 10, y + 19, w - 20, CommandPalette.ACCENT_TEAL);
                text(g, job.activity(), x + 10, y + 31, w - 20, CommandPalette.TEXT_MUTED);
                text(g, "Supplies: " + (job.supplies().isBlank() ? "No active requests reported" : job.supplies()),
                        x + 10, y + 43, w - 20, CommandPalette.ACCENT_GOLD);
            }
            // Unavailable progress is never painted as an empty/zero-percent bar.
            if (job.percent() >= 0) CommandFrame.progress(g, x + 10, y + h - 6, w - 20, 3,
                    job.percent() / 100f, CommandPalette.ACCENT_TEAL);
        }
    }

    static int constructionProgressColor(ConstructionReport.Job job) {
        return job.complete() ? CommandPalette.ACCENT_EMERALD
                : job.percent() == 100 ? CommandPalette.ACCENT_GOLD : CommandPalette.ACCENT_TEAL;
    }

    private void drawPerimeterProject(GuiGraphics g, ConstructionReport.Job job, int index, int count,
                                      int x, int y, int w, int h) {
        var building = buildingLayout();
        int progressColor = constructionProgressColor(job);
        CommandFrame.surface(g, x, y, w, h);
        text(g, (index + 1) + "/" + count + "  " + job.label(), x + 10, y + 5, w - 20, CommandPalette.TEXT);
        if (building.splitReport()) {
            text(g, "OVERALL PROGRESS", x + 10, y + 25, w - 20, CommandPalette.TEXT_DIM);
            drawWrappedText(g, job.progressText(), x + 10, y + 40, w - 20, 2, progressColor);
            if (job.percent() >= 0) CommandFrame.progress(g, x + 10, y + 65, w - 20, 4, job.percent() / 100f, progressColor);
            drawWrappedText(g, job.sectionText(), x + 10, y + 82, w - 20, 3, CommandPalette.ACCENT_GOLD);
            drawWrappedText(g, job.location(), x + 10, y + 124, w - 20, 2, CommandPalette.TEXT_MUTED);
            int detailX = building.reportDetailX(), detailW = building.reportDetailWidth();
            CommandFrame.surface(g, detailX, y, detailW, h);
            text(g, job.complete() ? "COMPLETE" : "CURRENT STATUS", detailX + 10, y + 8, detailW - 20,
                    job.complete() ? CommandPalette.ACCENT_EMERALD : CommandPalette.TEXT_DIM);
            drawWrappedText(g, job.activity(), detailX + 10, y + 25, detailW - 20, 5, CommandPalette.TEXT);
            CommandFrame.divider(g, detailX + 10, y + 83, detailW - 20);
            text(g, "NEEDED NOW", detailX + 10, y + 94, detailW - 20, CommandPalette.TEXT_DIM);
            drawWrappedText(g, job.supplies().isBlank() ? "No active requests reported" : job.supplies(),
                    detailX + 10, y + 109, detailW - 20, Math.max(1, (h - 152) / 10), CommandPalette.ACCENT_GOLD);
            if (job.cancelable()) text(g, "Placed blocks stay. No refund.", detailX + 10, y + h - 13,
                    detailW - 20, CommandPalette.TEXT_MUTED);
        } else {
            // Five explicit lines fit the minimum logical canvas. Full details remain in the report tooltip.
            text(g, job.progressText(), x + 10, y + 16, w - 20, progressColor);
            String[] sections = job.sectionText().split("\n", 2);
            text(g, sections[0], x + 10, y + 27, w - 20, CommandPalette.TEXT_MUTED);
            if (sections.length > 1) text(g, sections[1], x + 10, y + 38, w - 20, CommandPalette.ACCENT_GOLD);
            text(g, job.activity(), x + 10, y + 49, w - 20, job.complete() ? CommandPalette.ACCENT_EMERALD : CommandPalette.TEXT);
            if (h >= 82) {
                text(g, "Supplies: " + (job.supplies().isBlank() ? "No active requests reported" : job.supplies()),
                        x + 10, y + 65, w - 20, CommandPalette.ACCENT_GOLD);
                if (job.percent() >= 0) CommandFrame.progress(g, x + 10, y + h - 7, w - 20, 3, job.percent() / 100f, progressColor);
            }
        }
    }

    private void drawDefenses(GuiGraphics g, int mouseX, int mouseY) {
        switch (buildingSection) {
            case PERIMETER -> drawPerimeter(g);
            case STRUCTURES -> drawStructureSelection(g);
            case CONSTRUCTION -> drawConstruction(g);
        }
    }

    /** Real resident preview, essential figures and bounded guidance share one readable surface. */
    private void drawCivilians(GuiGraphics g, int mouseX, int mouseY) {
        int x = layout.x() + 10, y = layout.contentY(), w = layout.width() - 20;
        int h = layout.contentBottom() - y - 30;
        CommandFrame.surface(g, x, y, w, h);
        int portrait = Math.min(layout.compact() ? 48 : 96, Math.max(24, h - 28));
        CivilianPortrait.draw(g, x + 8, y + 8, portrait, mouseX, mouseY);
        int infoX = x + portrait + 18, infoW = w - portrait - 28;
        text(g, menu.civilians() + " / " + CivilianLedger.LIMIT + " residents", infoX, y + 8, infoW, CommandPalette.TEXT);
        text(g, "Housing: " + CoreCivilians.PRICE + "e from Treasury", infoX, y + 21, infoW, CommandPalette.ACCENT_GOLD);
        text(g, "Up to " + menu.civilians() + "e per in-game day", infoX, y + 34, infoW, CommandPalette.TEXT_MUTED);
        text(g, "Taxes collected: " + String.format(Locale.ROOT, "%,d", menu.totalCivilianTaxes()) + "e",
                infoX, y + 47, infoW, CommandPalette.ACCENT_EMERALD);
        int guidanceY = y + Math.max(portrait + 18, 65);
        int lines = Math.max(0, (y + h - guidanceY - 7) / 10);
        if (lines > 0) {
            drawWrappedText(g, civilianGuidance(layout.compact()), x + 10, guidanceY,
                    w - 20, lines, CommandPalette.TEXT_MUTED);
        }
    }

    /** Compact copy keeps the care and tax conditions complete instead of cutting a paragraph. */
    static String civilianGuidance(boolean compact) {
        if (compact) return "Provide beds, food and workstations. Taxes pause if stranded or the core is occupied.";
        return "Give residents beds, food and workstations. "
                + "Name, profession and appearance are assigned on arrival. "
                + "Each living resident pays one emerald per full in-game day. "
                + "Taxes pause if stranded or the core is occupied.";
    }

    private void drawPerimeter(GuiGraphics g) {
        var building = buildingLayout();
        int x = building.x(), y = building.bodyY(), w = building.perimeterPreviewWidth();
        text(g, "Auto perimeter", x + 3, y + 2, w - 6, CommandPalette.TEXT);
        if (building.splitPerimeter()) {
            text(g, "Your complete claimed boundary", x + 3, y + 17, w - 6, CommandPalette.TEXT_MUTED);
            int detailX = building.perimeterDetailX(), detailW = building.perimeterDetailWidth();
            CommandFrame.surface(g, detailX, y, detailW, building.bodyHeight());
            text(g, "ONE-TIME TREASURY FEE", detailX + 10, y + 10, detailW - 20, CommandPalette.TEXT_DIM);
            text(g, TerritoryFortification.PRICE + " emeralds", detailX + 10, y + 25,
                    detailW - 20, CommandPalette.ACCENT_GOLD);
            drawWrappedText(g, "Supply blocks separately through Builder-enabled storage.",
                    detailX + 10, y + 41, detailW - 20, 2, CommandPalette.TEXT_MUTED);
            CommandFrame.divider(g, detailX + 10, y + 66, detailW - 20);
            text(g, "WALL MATERIAL", detailX + 10, y + 76, detailW - 20, CommandPalette.TEXT_DIM);
            drawWrappedText(g, "Review is free. Charged once after the builder accepts.",
                    detailX + 10, building.perimeterTextY(), detailW - 20, 2, CommandPalette.TEXT_MUTED);
            text(g, "Review the actual terrain before commissioning.", x + 3, building.bottom() - 15,
                    w - 6, CommandPalette.TEXT_MUTED);
        } else {
            text(g, TerritoryFortification.PRICE + " emeralds · one-time Treasury fee", x + 3, y + 16,
                    w - 6, CommandPalette.ACCENT_GOLD);
            int lines = building.perimeterTextLines();
            String guidance = "Free review. Supply blocks separately; commission after the builder accepts.";
            if (building.illustratedPerimeter() || lines >= 6) guidance = "Review your actual claim in world before commissioning. "
                    + "Use an owned Workers builder and nearby storage with Builders enabled. "
                    + "The one-time Treasury fee and supplied materials are separate.";
            drawWrappedText(g, guidance, x + 3, building.perimeterTextY(), w - 6, lines, CommandPalette.TEXT_MUTED);
        }
        if (building.illustratedPerimeter()) {
            int exampleY = building.perimeterExampleY(), exampleHeight = building.perimeterExampleHeight();
            CommandFrame.surface(g, x, exampleY, w, exampleHeight);
            text(g, "TEMPLATE STYLE · ONE-CHUNK EXAMPLE", x + 8, exampleY + 7, w - 16, CommandPalette.TEXT_DIM);
            if (perimeterExamples[perimeterMaterial] == null)
                perimeterExamples[perimeterMaterial] = BuildingPlanThumbnail.perimeterExample(perimeterMaterial);
            perimeterExamples[perimeterMaterial].render(g, x + 8, exampleY + 21, w - 16, exampleHeight - 27);
        }
    }

    private void drawStructureSelection(GuiGraphics g) {
        var building = buildingLayout();
        if (!building.detailedCatalogue()) return;
        int x = building.detailX(), y = building.bodyY(), w = building.detailWidth();
        CommandFrame.card(g, x, y, w, building.bodyHeight() - 26, CommandPalette.ACCENT_TEAL);
        text(g, "SELECTED PLAN", x + 8, y + 8, w - 16, CommandPalette.TEXT_DIM);
        text(g, selectedDefense.label, x + 8, y + 23, w - 16, CommandPalette.ACCENT_TEAL);
        text(g, selectedDefense.dimensions(), x + 8, y + 38, w - 16, CommandPalette.TEXT_MUTED);
        int descriptionLines = Math.min(4, Math.max(1, (building.bodyHeight() - 130) / 10));
        drawWrappedText(g, selectedDefense.description, x + 8, y + 55, w - 16,
                descriptionLines, CommandPalette.TEXT_MUTED);
        int stepsY = y + 62 + descriptionLines * 10;
        drawWrappedText(g, "1. Take a free plan. 2. Preview the site and facing. 3. Confirm "
                        + selectedDefense.price + "e from the Treasury, plus supplied materials.",
                x + 8, stepsY, w - 16,
                Math.max(1, (building.actionY() - stepsY - 16) / 10), CommandPalette.ACCENT_GOLD);
    }

    private void drawTerritory(GuiGraphics g, int mouseX, int mouseY) {
        // Territory tab is a pure upgrade shop: four one-time faction-wide
        // purchases laid out as a 2x2 grid of tall cards. Each card shows
        // the label, a short description, price and status, with the
        // purchase button in the bottom-right (added in init()).
        int cols = 2;
        int gridTop = layout.contentY();
        int cellW = layout.territoryCardWidth();
        int cellH = layout.territoryCardHeight();
        for (int i = 0; i < TerritoryBuffs.COUNT; i++) {
            int col = i % cols, row = i / cols;
            int cx = layout.x() + 10 + col * (cellW + 8);
            int cy = gridTop + row * (cellH + 8);
            boolean owned = menu.hasTerritoryBuff(i);
            int accent = owned ? CommandPalette.ACCENT_EMERALD
                    : (canAfford(TerritoryBuffs.PRICES[i])
                            ? CommandPalette.ACCENT_GOLD
                            : CommandPalette.ACCENT_STEEL);
            if (owned) CommandFrame.cardDimmed(g, cx, cy, cellW, cellH);
            else CommandFrame.card(g, cx, cy, cellW, cellH, accent,
                    over(mouseX, mouseY, cx, cy, cellW, cellH));

            // Readable heading replaces the former ambiguous flag glyph.
            if (!layout.compact()) {
                String stateLabel = owned ? "ACTIVE" : "UPGRADE";
                int stateWidth = drawBadge(g, stateLabel, cx + cellW - 7,
                        cy + 6, accent);
                text(g, TerritoryBuffs.LABELS[i], cx + 10, cy + 10,
                        Math.max(1, cellW - stateWidth - 24), accent);
            } else {
                text(g, TerritoryBuffs.LABELS[i], cx + 10, cy + 10,
                        cellW - 20, accent);
            }

            // Multi-line description below the label.
            String desc = TerritoryBuffs.DESCRIPTIONS[i];
            if (layout.territoryDescriptionLines() > 0) {
                drawWrappedText(g, layout.compact() ? TerritoryBuffs.compactSummary(i) : desc,
                        cx + 10, cy + 28, cellW - 20, layout.territoryDescriptionLines(), CommandPalette.TEXT_MUTED);
            }
        }

        drawTerritoryOverview(g);
    }

    /** Uses the formerly empty Territory band as a compact kingdom summary. */
    private void drawTerritoryOverview(GuiGraphics g) {
        int available = layout.territoryFreeHeight();
        if (available < 18) return;
        int x = layout.x() + 10;
        int y = layout.territoryFreeTop();
        int w = layout.width() - 20;
        int h = available;
        int owned = ownedTerritoryBuffs();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_TEAL);
        text(g, "FACTION UPGRADES", x + 9, y + 6,
                Math.max(1, w / 3 - 18), CommandPalette.ACCENT_TEAL);
        String summary = owned + " of " + TerritoryBuffs.COUNT
                + " permanent upgrades active";
        text(g, summary, x + w / 3, y + 6,
                Math.max(1, w - w / 3 - 9), CommandPalette.TEXT);
        if (h >= 40) {
            CommandFrame.progress(g, x + 9, y + 19, w - 18, 5,
                    owned / (float) TerritoryBuffs.COUNT,
                    CommandPalette.ACCENT_TEAL);
            text(g, owned == TerritoryBuffs.COUNT
                            ? "All upgrades are active across your faction"
                            : "Permanent upgrades benefit your faction",
                    x + 9, y + 29, w - 18, CommandPalette.TEXT_DIM);
        }
        if (h >= 62) {
            CommandFrame.divider(g, x + 9, y + 43, w - 18);
            text(g, "BUILDING", x + 9, y + 50,
                    Math.max(1, w / 3 - 18), CommandPalette.ACCENT_GOLD);
            text(g, "Plan perimeters, collect structure plans and check nearby jobs in Building",
                    x + w / 3, y + 50,
                    Math.max(1, w - w / 3 - 9), CommandPalette.TEXT);
        }
        if (h >= 76) {
            text(g, "Construction uses Workers builders and materials from Builder-enabled storage",
                    x + 9, y + 64, w - 18, CommandPalette.TEXT_MUTED);
        }
    }

    /** Contextual band below the header: shows wave state or peacetime hint. */
    private void drawSiegeRibbon(GuiGraphics g, int x, int y, int w) {
        int wave = menu.currentWave();
        int vote = menu.voteSeconds();
        boolean siege = wave > 0;
        boolean voting = vote > 0;

        int accent = voting ? CommandPalette.ACCENT_ARCANE
                : siege ? CommandPalette.ACCENT_BLOOD
                : CommandPalette.ACCENT_STEEL;
        String status = layout.compact()
                ? (voting ? "RETREAT VOTE" : siege ? "WAVE " + wave : "KINGDOM WATCH")
                : (voting ? "RETREAT VOTE" : siege ? "SIEGE ACTIVE  |  Wave " + wave : "KINGDOM WATCH");
        String detail = layout.compact()
                ? (voting ? vote + "s left" : "Reward +" + String.format(Locale.ROOT, "%,d", menu.nextReward()) + "e")
                : (voting ? vote + "s remaining"
                        : siege ? "Next reward +" + menu.nextReward() + " to Treasury"
                        : "Treasury +" + menu.nextReward() + " on next wave clear");

        int ribbonY = layout.ribbonY();

        CommandFrame.card(g, x + 10, ribbonY, w - 20, CoreHireLayout.RIBBON_HEIGHT, accent);
        text(g, status, x + 16, ribbonY + 4,
                Math.max(1, w / 2 - 20), accent);
        text(g, detail, x + w / 2, ribbonY + 4,
                Math.max(1, w / 2 - 14), CommandPalette.TEXT);
    }

    private void drawHire(GuiGraphics g, int i, int mouseX, int mouseY) {
        int x = layout.cardX(i), y = layout.cardY(i);
        int w = layout.cardWidth(), h = layout.cardHeight();
        int role = menu.role(i);

        int accent = i == 3 ? CommandPalette.ACCENT_ARCANE
                : i == 2 ? CommandPalette.ACCENT_TEAL
                : CommandPalette.ACCENT_GOLD;

        boolean hovered = over(mouseX, mouseY, x, y, w, h);
        if (menu.sold(i)) CommandFrame.cardDimmed(g, x, y, w, h);
        else CommandFrame.card(g, x, y, w, h, accent, hovered);

        if (role < 0 || role >= CoreHiring.NAMES.length) {
            text(g, menu.rotation() <= 0 ? "Loading offers…" : "Offer unavailable", x + 8, y + 8,
                    w - 16, CommandPalette.TEXT_MUTED);
            return;
        }

        if (layout.compact()) {
            drawCompactHire(g, i, role, x, y, w, h, mouseX, mouseY);
            return;
        }

        // Portrait tile on the left with heraldic corner brackets around it,
        // matching the reference kingdom-command mockup.
        int portrait = hirePortraitSize();
        int px = x + 6, py = y + 6;
        EntityPortrait.draw(g, role, px, py, portrait, mouseX, mouseY, i, menu);
        // Heraldic bracket ornaments at each corner of the portrait.
        CommandFrame.cornerBracket(g, px + 1, py + 1, +1, +1);
        CommandFrame.cornerBracket(g, px + portrait - 2, py + 1, -1, +1);
        CommandFrame.cornerBracket(g, px + 1, py + portrait - 2, +1, -1);
        CommandFrame.cornerBracket(g, px + portrait - 2, py + portrait - 2, -1, -1);

        // Info column to the right of the portrait.
        int infoLeft = px + portrait + 8;
        int infoWidth = w - portrait - 18;
        // Character name in white.
        text(g, CoreHiring.NAMES[role], infoLeft, y + 8, infoWidth, CommandPalette.TEXT);
        String roleLabel = i == 3 ? CoreHiring.rarity(role) + " Hero"
                : i == 2 ? "Worker" : shortRole(role);
        text(g, menu.sold(i) ? roleLabel + " · Hired" : roleLabel,
                infoLeft, y + 20, infoWidth, menu.sold(i) ? CommandPalette.TEXT_DIM : accent);

        // Descriptive blurb: role summary for regular recruits/workers,
        // ability line for heroes. Wrapped across up to three lines so the
        // player doesn't have to hover the tooltip to know what they're
        // hiring.
        String blurb = i == 3 ? HeroTraits.description(role) : roleBlurb(role);
        int blurbY = y + 34;
        int chipY = y + h - 42;
        int maxBlurbLines = Math.max(1, Math.min(3,
                (chipY - blurbY - 2) / 10));
        int blurbLines = drawWrappedText(g, blurb, infoLeft, blurbY,
                infoWidth, maxBlurbLines, CommandPalette.TEXT_MUTED);

        // Stat/armor line: shows the loadout tier text and the armor material
        // so players understand what armor the unit is going to wear. Regular
        // recruits are cloth/leather; workers wear their tool kit; heroes wear
        // a themed trimmed set from HeroTraits.
        String kit = kitDescriptor(role);
        int kitY = blurbY + Math.min(blurbLines, 3) * 10 + 2;
        if (kitY + 9 < chipY) {
            text(g, kit, infoLeft, kitY, infoWidth, CommandPalette.ACCENT_TEAL);
        }

        // Item chip in the bottom-left of the info column: shows the recruit's
        // signature item (bread, arrows, tool). Sits above the Hire button.
        g.renderItem(menu.getSlot(i).getItem(), infoLeft, chipY);
        // Cost readout next to the item so the player sees the price at a
        // glance without hovering.
        String price = menu.sold(i) ? "Hired"
                : menu.cost(i) < 0 ? "--" : String.format(Locale.ROOT, "%,d", menu.cost(i)) + "e";
        int priceColor = menu.sold(i) || menu.cost(i) < 0
                ? CommandPalette.TEXT_DIM
                : canAfford(menu.cost(i))
                        ? CommandPalette.ACCENT_EMERALD
                        : CommandPalette.ACCENT_BLOOD;
        text(g, price, infoLeft + 20, chipY + 4,
                infoWidth - 20, priceColor);
    }

    /**
     * Dense Army card used when Minecraft's scaled GUI is narrow or short.
     * It deliberately keeps only the identity, role and price in the card;
     * the full kit/ability detail remains available through the card tooltip.
     */
    private void drawCompactHire(GuiGraphics g, int slot, int role,
                                 int x, int y, int w, int h,
                                 int mouseX, int mouseY) {
        int portrait = hirePortraitSize();
        int px = x + 5;
        int py = y + Math.max(4, (h - portrait) / 2 - 1);
        EntityPortrait.draw(g, role, px, py, portrait, mouseX, mouseY, slot, menu);

        int infoLeft = px + portrait + 6;
        int infoWidth = Math.max(1, x + w - infoLeft - 5);
        text(g, CoreHiring.NAMES[role], infoLeft, y + 5,
                infoWidth, CommandPalette.TEXT);
        text(g, slot == 3 ? "Hero" : slot == 2 ? "Worker" : shortRole(role),
                infoLeft, y + 16, infoWidth, CommandPalette.TEXT_MUTED);
    }

    /**
     * Draw text wrapped to a max width across up to maxLines lines. Returns
     * the number of lines actually rendered so callers can position the
     * next element under it.
     */
    private int drawWrappedText(GuiGraphics g, String s, int x, int y, int width,
                                int maxLines, int color) {
        if (s == null || s.isEmpty()) return 0;
        List<FormattedCharSequence> lines = wrapped(s, width);
        int drawn = Math.min(maxLines, lines.size());
        for (int i = 0; i < drawn; i++) {
            g.drawString(font, lines.get(i), x, y + i * 10, color, false);
        }
        return drawn;
    }

    /** One-sentence description for non-hero recruits and workers. */
    private static String roleBlurb(int role) {
        return switch (role) {
            case 0 -> "Front-line recruit. Sword and shield. Cheapest hire, good in numbers.";
            case 1 -> "Shield wall anchor. Absorbs melee pressure so archers can work.";
            case 2 -> "Ranged skirmisher. Bow. Break-away kites best of the recruit line.";
            case 3 -> "Heavy ranged. Crossbow bolts pierce armor. Slow to reload.";
            case 4 -> "Tends crops in the war camp. Feeds the whole faction.";
            case 5 -> "Chops trees near the camp. Keeps timber flowing for repairs.";
            case 6 -> "Mines stone and coal nearby. Restocks fortification materials.";
            case 7 -> "Repairs damaged blocks after a siege ends. Speeds recovery.";
            case 8 -> "Cooks raw ingredients into food. Multiplies farmer output.";
            case 9 -> "Runs goods between chests. Ties your logistics together.";
            default -> "Faction unit.";
        };
    }

    /** Loadout / armor hint shown under the blurb. */
    private static String kitDescriptor(int role) {
        if (CoreHiring.isHero(role)) {
            String armor = switch (CoreHiring.heroTier(role)) {
                case 0 -> "iron";
                case 4 -> "netherite";
                default -> "diamond";
            };
            return "Kit: " + armor + " themed armor, " + HeroTraits.weaponName(role);
        }
        return switch (role) {
            case 0 -> "Kit: leather cap, iron sword, wooden shield";
            case 1 -> "Kit: iron helm, iron sword, iron shield";
            case 2 -> "Kit: leather cap, bow, arrows";
            case 3 -> "Kit: chain helm, crossbow, tipped bolts";
            case 4 -> "Kit: straw hat, hoe, seeds";
            case 5 -> "Kit: leather cap, iron axe";
            case 6 -> "Kit: iron helm, iron pickaxe, torches";
            case 7 -> "Kit: leather cap, hammer, timber";
            case 8 -> "Kit: chef hat, iron knife, cook pot";
            case 9 -> "Kit: leather boots, satchel, map";
            default -> "";
        };
    }

    private static String shortRole(int role) {
        if (CoreHiring.isHero(role)) return "Hero";
        return switch (role) {
            case 0 -> "Recruit";
            case 1 -> "Shieldman";
            case 2 -> "Archer";
            case 3 -> "Crossbowman";
            case 4 -> "Farmer";
            case 5 -> "Lumberjack";
            case 6 -> "Miner";
            case 7 -> "Builder";
            case 8 -> "Cook";
            case 9 -> "Courier";
            default -> "Unit";
        };
    }

    private void drawLoot(GuiGraphics g, int i, int mouseX, int mouseY) {
        int x = layout.cardX(0), y = layout.marketY(i);
        int w = layout.cardWidth(), h = layout.marketHeight();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_GOLD,
                over(mouseX, mouseY, x, y, w, h));

        boolean opening = revealBox == i && revealTicks > 0;
        boolean done = revealBox == i && revealTicks == 0 && !revealed.isEmpty();

        if (layout.compact()) {
            if (done) g.renderItem(revealed, x + 4, y + h - 19);
            text(g, done ? revealed.getHoverName().getString() : CoreLoot.NAMES[i],
                    x + 8, y + 5, w - 16,
                    done ? CommandPalette.tier(revealedTier) : CommandPalette.TEXT);
            if (opening) CommandFrame.progress(g, x + 8, y + 17, w - 16, 3,
                    1f - revealTicks / (float) CoreLoot.OPEN_TICKS, CommandPalette.ACCENT_ARCANE);
            return;
        }

        // Show the real reward item after unsealing. Before opening, readable
        // text carries the tier instead of an unexplained miniature glyph.
        int textLeft = x + 8;
        if (done) {
            CommandFrame.chip(g, x + 6, y + 5, 20, 20, CommandPalette.ACCENT_GOLD);
            g.renderItem(revealed, x + 8, y + 7);
            textLeft = x + 30;
        }

        String lootState = opening ? "OPENING"
                : done ? CoreLoot.rarity(revealedTier).toUpperCase(Locale.ROOT)
                : "SEALED";
        int stateColor = opening ? CommandPalette.ACCENT_ARCANE
                : done ? CommandPalette.tier(revealedTier)
                : CommandPalette.ACCENT_GOLD;
        int badgeWidth = drawBadge(g, lootState, x + w - 6, y + 5, stateColor);
        text(g, CoreLoot.NAMES[i], textLeft, y + 6,
                Math.max(1, x + w - textLeft - badgeWidth - 10), CommandPalette.TEXT);
        String subtitle = opening ? "Unsealing the seal..."
                : done ? revealed.getHoverName().getString()
                : CoreLoot.floorTier(i) > 0
                        ? CoreLoot.rarity(CoreLoot.floorTier(i)) + " floor  |  Epic ceiling"
                        : "Sealed Loot Box  |  " + CoreLoot.odds();
        text(g, subtitle, textLeft, y + 17, x + w - textLeft - 6,
                opening ? CommandPalette.ACCENT_ARCANE
                        : done ? CommandPalette.tier(revealedTier)
                        : CommandPalette.TEXT_MUTED);

        // Price / progress line sits directly under the subtitle; the Open
        // button occupies the bottom band of the (now shorter) card.
        int infoY = y + 28;
        if (opening) {
            float progress = 1f - (revealTicks / (float) CoreLoot.OPEN_TICKS);
            CommandFrame.progress(g, x + 8, infoY, w - 16, 4,
                    progress, CommandPalette.ACCENT_ARCANE);

        }
    }

    /** Free band under the market becomes a readable reliquary protocol. */
    private void drawLootReserve(GuiGraphics g) {
        int h = layout.marketFreeHeight();
        if (h < 16) return;
        int x = layout.x() + 10;
        int w = layout.width() - 20;
        int y = layout.marketFreeTop();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_STEEL);
        int badgeWidth = 0;
        if (!layout.compact() && w >= 260) {
            badgeWidth = drawBadge(g, "REWARDS HIDDEN", x + w - 7, y + 5,
                    CommandPalette.ACCENT_ARCANE);
        }
        text(g, "OLYMPIAN ARMORY", x + 9, y + 7,
                Math.max(1, w - badgeWidth - 24), CommandPalette.ACCENT_STEEL);
        if (h >= 30) {
            text(g, "Every box holds enchanted Olympian equipment and supplies. Open it to discover your prize.",
                    x + 9, y + 19, w - 18, CommandPalette.TEXT_MUTED);
        }
        if (h >= 54) {
            CommandFrame.divider(g, x + 9, y + 35, w - 18);
            text(g, "FIELD", x + 9, y + 42, w / 3 - 12, CommandPalette.TEXT);
            text(g, "VETERAN", x + w / 3, y + 42, w / 3 - 12, CommandPalette.ACCENT_EMERALD);
            text(g, "ROYAL", x + (w * 2) / 3, y + 42, w / 3 - 12, CommandPalette.ACCENT_GOLD);
        }
        if (h >= 62) {
            text(g, "Any rarity", x + 9, y + 53, w / 3 - 12, CommandPalette.TEXT_DIM);
            text(g, "Uncommon floor", x + w / 3, y + 53, w / 3 - 12, CommandPalette.TEXT_DIM);
            text(g, "Rare floor", x + (w * 2) / 3, y + 53, w / 3 - 12, CommandPalette.TEXT_DIM);
        }
        if (h >= 90) {
            CommandFrame.divider(g, x + 9, y + 72, w - 18);
            text(g, "Paid from the faction Treasury  ·  Equipment, provisions and distinct supplies",
                    x + 9, y + 79, w - 18, CommandPalette.TEXT_DIM);
        }
    }

    private void drawBuff(GuiGraphics g, int i, int mouseX, int mouseY) {
        int x = layout.cardX(1), y = layout.marketY(i);
        int w = layout.cardWidth(), h = layout.marketHeight();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_TEAL,
                over(mouseX, mouseY, x, y, w, h));

        if (layout.compact()) {
            text(g, CoreBuffs.NAMES[i], x + 8, y + 5, w - 16, CommandPalette.TEXT);
            return;
        }
        boolean active = minecraft != null && minecraft.player != null
                && minecraft.player.hasEffect(CoreBuffs.effect(i));
        String state = active ? "ACTIVE" : "5 MIN";
        int stateColor = active ? CommandPalette.ACCENT_EMERALD : CommandPalette.ACCENT_TEAL;
        int badgeWidth = drawBadge(g, state, x + w - 6, y + 5, stateColor);
        text(g, CoreBuffs.NAMES[i], x + 8, y + 6,
                Math.max(1, w - badgeWidth - 20), CommandPalette.TEXT);
        text(g, CoreBuffs.DETAILS[i], x + 8, y + 17, w - 16, CommandPalette.TEXT_MUTED);
    }


    private void drawFaction(GuiGraphics g) {
        int x = layout.x();
        int w = layout.width();

        // Treasury overview: one compact summary on small screens, three
        // glanceable KPI cards on roomy screens.
        int bankCardH = 46;
        int bankY = layout.contentY();
        long dailyInterest = (long) menu.bank() * menu.interestRate() / 10000L;
        String interestCountdown = formatInterestCountdown(menu.ticksUntilInterest());
        if (layout.compact()) {
            CommandFrame.card(g, x + 10, bankY, w - 20, bankCardH,
                    CommandPalette.ACCENT_EMERALD);
            int leftX = x + 18;
            int compactW = w - (leftX - x) - 14;
            text(g, menu.factionName(), leftX, bankY + 6,
                    compactW, CommandPalette.TEXT);
            text(g, String.format(Locale.ROOT, "Treasury %,de  |  Next +%,de",
                            menu.bank(), menu.nextReward()),
                    leftX, bankY + 18, compactW, CommandPalette.ACCENT_EMERALD);
            text(g, String.format(Locale.ROOT, "Interest +%,d in %s  |  Wave %d",
                            dailyInterest, interestCountdown, menu.nextWave()),
                    leftX, bankY + 30, compactW, CommandPalette.TEXT_MUTED);
        } else {
            int gap = 6;
            int cardsW = w - 20;
            int metricW = (cardsW - gap * 2) / 3;
            drawMetricCard(g, x + 10, bankY, metricW, bankCardH,
                    "TREASURY", String.format(Locale.ROOT, "%,de", menu.bank()),
                    String.format(Locale.ROOT, "Civilian taxes +%,de", menu.lastCivilianTaxes()), CommandPalette.ACCENT_EMERALD);
            drawMetricCard(g, x + 10 + metricW + gap, bankY, metricW, bankCardH,
                    "NEXT REWARD", String.format(Locale.ROOT, "+%,de", menu.nextReward()),
                    "Wave " + menu.nextWave(), CommandPalette.ACCENT_TEAL);
            drawMetricCard(g, x + 10 + (metricW + gap) * 2, bankY,
                    cardsW - (metricW + gap) * 2, bankCardH,
                    "DAILY INTEREST", String.format(Locale.ROOT, "+%,de", dailyInterest),
                    interestCountdown + "  ·  "
                            + String.format(Locale.ROOT, "%.2f%%", menu.interestRate() / 100.0),
                    CommandPalette.ACCENT_GOLD);
        }

        // Roster panel below (pushed down to make room for the taller bank card
        // and its Deposit/Withdraw button row). We split it into a left roster
        // column and a right activity-graph column when the roster is short
        // enough to leave room; otherwise the roster takes the full width.
        int rosterY = bankRosterY();
        int rosterH = bankRosterHeight();
        CommandFrame.card(g, x + 10, rosterY, w - 20, rosterH, CommandPalette.ACCENT_ARCANE);

        boolean showGraph = !layout.compact() && rosterH >= 82;
        int graphW = showGraph ? Math.min(180, (w - 20) / 2 - 8) : 0;
        int rosterListW = showGraph ? w - 32 - graphW - 8 : w - 36;
        int listTop = rosterY + 22;
        int lines = Math.max(1, (rosterH - 26) / 12);
        int count = menu.members().size();
        int maxOffset = Math.max(0, count - lines);
        rosterOffset = Math.max(0, Math.min(rosterOffset, maxOffset));

        String indicator = count > lines
                ? (rosterOffset + 1) + "-" + Math.min(count, rosterOffset + lines) + "/" + count + " Scroll"
                : count + " members";
        int indicatorW = Math.min(rosterListW / 2, font.width(indicator));
        text(g, "FACTION ROSTER", x + 18, rosterY + 6, rosterListW - indicatorW - 8,
                CommandPalette.ACCENT_ARCANE);
        text(g, indicator, x + 18 + rosterListW - indicatorW, rosterY + 6,
                indicatorW, CommandPalette.TEXT_DIM);

        if (count == 0) {
            text(g, "Loading faction members…", x + 18, listTop, rosterListW,
                    CommandPalette.TEXT_MUTED);
        } else {
            for (int i = 0; i < lines && i + rosterOffset < count; i++) {
                int rowY = listTop + i * 12;
                if ((i & 1) == 0) {
                    g.fill(x + 16, rowY - 2, x + 16 + rosterListW,
                            rowY + 10, 0x261f2c42);
                }
                String member = menu.members().get(i + rosterOffset);
                boolean online = member.startsWith("Online  ");
                g.fill(x + 18, rowY + 2, x + 20, rowY + 7,
                        online ? CommandPalette.ACCENT_EMERALD : CommandPalette.TEXT_DIM);
                text(g, member, x + 24, rowY, rosterListW - 8,
                        online ? CommandPalette.TEXT : CommandPalette.TEXT_MUTED);
            }
        }
        // Activity graph: right side of the roster panel. Uses the recent
        // bank ledger (deposits, withdrawals, wave rewards) as bars centered
        // on a zero line. Green above = credit, red below = debit.
        if (showGraph) {
            int gx = x + 18 + rosterListW + 8;
            int gy = rosterY + 22;
            int gh = rosterH - 30;
            drawBankGraph(g, gx, gy, graphW, gh);
        }
    }

    private void drawMetricCard(GuiGraphics g, int x, int y, int w, int h,
                                String label, String value, String detail, int accent) {
        CommandFrame.card(g, x, y, w, h, accent);
        text(g, label, x + 9, y + 6, w - 18, CommandPalette.TEXT_DIM);
        text(g, value, x + 9, y + 18, w - 18, accent);
        text(g, detail, x + 9, y + 30, w - 18, CommandPalette.TEXT_MUTED);
    }

    private int bankRosterY() {
        return layout.contentY() + 74;
    }

    private int bankRosterHeight() {
        return Math.max(34, layout.contentBottom() - bankRosterY());
    }

    /** v4.18.0 bank activity sparkline. Renders a zero-centered bar chart. */
    private void drawBankGraph(GuiGraphics g, int gx, int gy, int gw, int gh) {
        // Inset analytics panel inside the roster card.
        g.fill(gx, gy, gx + gw, gy + gh, CommandPalette.CHIP_BORDER);
        g.fillGradient(gx + 1, gy + 1, gx + gw - 1, gy + gh - 1,
                CommandPalette.HEADER_TOP, CommandPalette.CHIP_FILL);
        g.fill(gx + 1, gy + 1, gx + 3, gy + gh - 1,
                CommandPalette.ACCENT_EMERALD);
        text(g, "RECENT ACTIVITY", gx + 7, gy + 4, gw - 12,
                CommandPalette.ACCENT_GOLD);

        int[] ledger = menu.bankLedger();
        int plotTop = gy + 16;
        int plotBottom = gy + gh - 12;
        int plotH = plotBottom - plotTop;
        int zeroY = plotTop + plotH / 2;
        // Zero line.
        g.fill(gx + 5, zeroY, gx + gw - 5, zeroY + 1,
                CommandPalette.DIVIDER);

        if (ledger.length == 0) {
            text(g, "No recent transactions", gx + 6, zeroY + 6, gw - 12, CommandPalette.TEXT_DIM);
            return;
        }
        // Find max absolute delta for scaling.
        long maxAbs = 1;
        for (int d : ledger) maxAbs = Math.max(maxAbs, Math.abs((long) d));
        int plotW = gw - 8;
        int barW = Math.max(2, plotW / Math.max(ledger.length, 8));
        int startX = gx + 4 + (plotW - barW * ledger.length) / 2;
        long totalCredit = 0, totalDebit = 0;
        for (int i = 0; i < ledger.length; i++) {
            int d = ledger[i];
            int bx = startX + i * barW;
            int barH = (int) (Math.abs((long) d) * (plotH / 2 - 2) / maxAbs);
            if (d >= 0) {
                g.fill(bx + 1, zeroY - barH, bx + barW - 1, zeroY,
                        CommandPalette.ACCENT_EMERALD);
                totalCredit += d;
            } else {
                g.fill(bx + 1, zeroY + 1, bx + barW - 1, zeroY + 1 + barH,
                        CommandPalette.ACCENT_BLOOD);
                totalDebit -= (long) d;
            }
        }
        // Footer totals.
        String footer = String.format(Locale.ROOT, "+%d  /  -%d over last %d",
                totalCredit, totalDebit, ledger.length);
        text(g, footer, gx + 6, gy + gh - 10, gw - 12, CommandPalette.TEXT_MUTED);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        x = layout.logicalX(x);
        y = layout.logicalY(y);
        if (CoreTabStrip.contains(x, y, layout.x() + 10, layout.tabY(),
                layout.width() - 20, CoreHireLayout.TAB_HEIGHT) && delta != 0) {
            movePage(delta > 0 ? -1 : 1);
            return true;
        }
        if (tab == CoreCommandPage.TREASURY && CoreTabStrip.contains(x, y,
                layout.x() + 10, bankRosterY(), layout.width() - 20, bankRosterHeight())) {
            int count = menu.members().size();
            int rosterH = bankRosterHeight();
            int lines = Math.max(1, (rosterH - 26) / 12);
            int maxOffset = Math.max(0, count - lines);
            int step = (int) -Math.signum(delta);
            rosterOffset = Math.max(0, Math.min(maxOffset, rosterOffset + step));
            return true;
        }
        if (tab == CoreCommandPage.INTEL && CoreTabStrip.contains(x, y,
                intelBodyX, intelBodyY, intelBodyW, intelBodyH)) {
            int step = (int) -Math.signum(delta) * 12;
            intelOffset = Math.max(0, Math.min(intelMaxOffset, intelOffset + step));
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = layout.logicalX(mouseX);
        mouseY = layout.logicalY(mouseY);
        if (tab == CoreCommandPage.INTEL && button == 0) {
            // Scrollbar hit-test: 8px wide gutter on the right edge of the body panel.
            int trackX = intelBodyX + intelBodyW - 8;
            int trackY = intelBodyY + 4;
            int trackH = intelBodyH - 8;
            if (mouseX >= trackX && mouseX < trackX + 4
                    && mouseY >= trackY && mouseY < trackY + trackH
                    && intelMaxOffset > 0) {
                intelDragging = true;
                intelDragGrabY = (int) mouseY;
                intelDragStartOffset = intelOffset;
                // Also jump the thumb to the click point immediately so
                // click-anywhere on the track scrolls there (standard behavior).
                int thumbH = Math.max(16, (int) ((long) trackH * intelBodyH
                        / Math.max(1, intelBodyH + intelMaxOffset)));
                int thumbTravel = Math.max(1, trackH - thumbH);
                int newOffset = (int) ((mouseY - trackY - thumbH / 2.0)
                        * intelMaxOffset / thumbTravel);
                intelOffset = Math.max(0, Math.min(intelMaxOffset, newOffset));
                intelDragStartOffset = intelOffset;
                return true;
            }
        }
        // Vanilla focuses the clicked child after its callback. A pager may now be disabled.
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        ensureVisibleFocus();
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = layout.logicalX(mouseX);
        mouseY = layout.logicalY(mouseY);
        if (intelDragging && button == 0) { intelDragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dx, double dy) {
        mouseX = layout.logicalX(mouseX);
        mouseY = layout.logicalY(mouseY);
        dx /= layout.scale();
        dy /= layout.scale();
        // Territory tab is now a pure upgrade shop; no map drag handling.
        if (tab == CoreCommandPage.INTEL && intelDragging && button == 0 && intelMaxOffset > 0) {
            // Map the mouse's Y travel back into scroll offset via the
            // thumb's travel range. Same formula as the draw call.
            int trackH = intelBodyH - 8;
            int thumbH = Math.max(16, (int) ((long) trackH * intelBodyH
                    / Math.max(1, intelBodyH + intelMaxOffset)));
            int thumbTravel = Math.max(1, trackH - thumbH);
            int deltaPx = (int) mouseY - intelDragGrabY;
            int newOffset = intelDragStartOffset + (int) ((long) deltaPx
                    * intelMaxOffset / thumbTravel);
            intelOffset = Math.max(0, Math.min(intelMaxOffset, newOffset));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public void onClose() {
        buildingReport.update(false, this::action);
        EntityPortrait.clear();
        CivilianPortrait.clear();
        super.onClose();
    }

    @Override
    public void removed() {
        buildingReport.update(false, this::action);
        super.removed();
    }

    /**
     * v4.28.8: format an in-game tick countdown as a short human string.
     * 20 ticks = 1 second of active play, 24000 ticks = one Minecraft day.
     * Examples: 24000 -> "1d", 6000 -> "5m", 200 -> "10s", 0 -> "soon".
     */
    static String formatInterestCountdown(int ticks) {
        if (ticks <= 0) return "soon";
        long seconds = ticks / 20L;
        if (seconds <= 0) return "soon";
        long days = seconds / 1200L; // 20 real minutes = 1 in-game day
        long remSec = seconds - days * 1200L;
        long minutes = remSec / 60L;
        long finalSec = remSec - minutes * 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d");
        if (minutes > 0) { if (sb.length() > 0) sb.append(' '); sb.append(minutes).append("m"); }
        if (sb.length() == 0) sb.append(finalSec).append("s");
        return sb.toString();
    }
}
