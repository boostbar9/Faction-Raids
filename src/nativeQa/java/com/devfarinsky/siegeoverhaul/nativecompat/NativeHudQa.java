package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidEvents;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.client.CoreHireScreen;
import com.devfarinsky.siegeoverhaul.client.EntityPortrait;
import com.devfarinsky.siegeoverhaul.client.ProtectedConstructionScreen;
import com.devfarinsky.siegeoverhaul.core.CoreBuildingLayout;
import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.client.SiegeCommandScreen;
import com.devfarinsky.siegeoverhaul.client.SiegeOverhaulConfigScreen;
import com.devfarinsky.siegeoverhaul.core.CivilianLedger;
import com.devfarinsky.siegeoverhaul.core.ConstructionReport;
import com.devfarinsky.siegeoverhaul.core.CoreCommandPage;
import com.devfarinsky.siegeoverhaul.core.CoreHireLayout;
import com.devfarinsky.siegeoverhaul.core.CoreHireMenu;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Opt-in, unshipped real Minecraft HUD acceptance. Values are explicitly labeled
 * client-menu samples, never evidence of server claims, payments or construction.
 * No paid action, free-plan/review request or native gameplay guard is bypassed.
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeHudQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && "hud".equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    private static final String WORLD = "siege-native-hud-fixture";
    private static final long SECOND = 1_000_000_000L;
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<String> CHECKS = new ArrayList<>(), SHOTS = new ArrayList<>();
    private static final List<Map<String, Object>> VIEWS = new ArrayList<>();
    private static final List<Step> STEPS = new ArrayList<>();
    private static Path directory, evidence;
    private static int phase, ticks, readyAt, frame, captureFrame, step;
    private static int requestedWidth = 960, requestedHeight = 720, requestedScale = 2;
    private static long started, resizeStarted, captureStarted;
    private static boolean finished, resizing;
    private static String capture, fixture = "No fixture open";
    private static Robot keyboard;
    private static CompletableFuture<UUID> inspectionSetup;
    private static UUID inspectionArea;
    private static final List<Integer> HELD_KEYS = new ArrayList<>();
    private record Step(String name, CheckedAction action) {}
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    private NativeHudQa() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            ticks++;
            if (started == 0) started = System.nanoTime();
            require(System.nanoTime() - started < 8 * 60 * SECOND, "HUD fixture exceeded eight-minute deadline");
            if (capture != null) {
                require(System.nanoTime() - captureStarted < 15 * SECOND, "Framebuffer capture timed out: " + capture);
                return;
            }
            if (resizing) {
                require(System.nanoTime() - resizeStarted < 10 * SECOND, "Actual QA viewport did not settle: " + viewport(mc));
                if (mc.getWindow().getWidth() != requestedWidth || mc.getWindow().getHeight() != requestedHeight
                        || mc.getWindow().getScreenWidth() != requestedWidth || mc.getWindow().getScreenHeight() != requestedHeight) return;
                mc.options.guiScale().set(requestedScale);
                mc.resizeDisplay();
                require(mc.getWindow().getGuiScaledWidth() == requestedWidth / requestedScale
                                && mc.getWindow().getGuiScaledHeight() == requestedHeight / requestedScale,
                        "Requested actual GUI scale was not reached: " + viewport(mc));
                resizing = false; readyAt = ticks + 4; return;
            }
            if (ticks < readyAt) return;
            if (phase == 0) {
                if (!(mc.screen instanceof TitleScreen)) return;
                initialize(mc);
                requestViewport(960, 720, 2); phase = 1; return;
            }
            if (phase == 1) {
                require(!Files.exists(directory.resolve("saves").resolve(WORLD)), "Refusing an existing HUD fixture world");
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                phase = 2;
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, false,
                                rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261004L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                return;
            }
            if (phase == 2) {
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) return;
                require(mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                .toRealPath().equals(directory.resolve("saves").resolve(WORLD).toRealPath()),
                        "Active integrated world is outside the isolated fixture");
                inspectionSetup = new CompletableFuture<>();
                UUID owner = mc.player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    try { inspectionSetup.complete(setUpInspection(mc.getSingleplayerServer().getPlayerList().getPlayer(owner))); }
                    catch (Throwable failure) { inspectionSetup.completeExceptionally(failure); }
                });
                phase = 3; readyAt = ticks + 40; return;
            }
            require(mc.player != null && mc.level != null, "Fresh HUD fixture player/world disappeared");
            if (phase == 3) {
                if (!inspectionSetup.isDone()) return;
                inspectionArea = inspectionSetup.join();
                if (clientInspection() == null) return;
                buildSteps(); phase = 4;
            }
            // The Codex's real read-only sync may receive the empty world's data.
            // Keep this explicitly labeled display sample, never claim it came from that server.
            if (mc.screen instanceof SiegeCommandScreen codex) codex.updateSnapshot(dashboard());
            if (step == STEPS.size()) { finish(mc, null); return; }
            Step current = STEPS.get(step++);
            REPORT.put("currentStep", current.name());
            current.action().run();
            readyAt = ticks + 4;
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void buildSteps() {
        for (int scale : List.of(2, 3)) {
            String prefix = "compact-scale" + scale;
            add(prefix + " viewport", () -> requestViewport(960, 720, scale));
            coreMatrix(prefix);
            stateMatrix(prefix);
            keyboardAndScroll(prefix);
            secondaryMatrix(prefix);
            inspectionMatrix(prefix);
        }
        // The 960px fixture exercises compact cards at both scales. A genuinely
        // roomy viewport also exercises full-card layouts, rather than relabeling it.
        add("roomy viewport", () -> requestViewport(1440, 960, 2));
        coreMatrix("roomy-scale2");
        add("roomy GUI scale 1", () -> requestViewport(1440, 960, 1));
        coreMatrix("roomy-scale1");
        inspectionMatrix("roomy-scale1");
        add("reopen Core after inspection", () -> openCore("resize sample", true, true));
        add("window resize preserves Intel page and search", () -> {
            selectPage(CoreCommandPage.INTEL);
            search().setValue("QA resize text");
            requestViewport(960, 720, 3);
        });
        add("verify resized Intel", () -> {
            require(currentPage() == CoreCommandPage.INTEL && search().getValue().equals("QA resize text"),
                    "Actual window resize lost selected page or search");
            check("Actual 1440x960 to 960x720 window resize preserves Intel page/query");
            capture("resized-intel-preserved");
        });
        add("close by header hitbox", () -> {
            click("X"); require(mc().screen == null, "Header close did not dismiss HUD");
            openCore("fresh reopen", false, false);
            require(currentPage() == CoreCommandPage.ARMY, "Fresh reopen retained an unrelated page");
            require(read("confirmBox").equals(-1), "Fresh reopen retained loot confirmation");
            click("X"); require(mc().screen == null, "Repeated header close failed");
            check("Header close and repeated fresh reopen return cleanly to the game");
        });
    }

    private static void coreMatrix(String prefix) {
        add(prefix + " sample menu", () -> openCore("mixed sample states", true, true));
        for (CoreCommandPage page : CoreCommandPage.values()) {
            add(prefix + " " + page.label(), () -> { selectPage(page); });
            add(prefix + " capture " + page.label(), () -> capture(prefix + "-" + slug(page.label())));
            if (page == CoreCommandPage.LOOT) {
                add(prefix + " possible epic rewards", () -> {
                    click(((Button[]) read("lootPreviews"))[0]);
                    require((Boolean) read("showingLootGallery"), "Items did not open the read-only gallery");
                    require((Integer) read("confirmBox") == -1, "Gallery retained paid confirmation");
                    require(!((Button[]) read("boxes"))[0].visible, "Purchase controls overlap gallery");
                });
                add(prefix + " epic gallery capture", () -> capture(prefix + "-loot-gallery-epic"));
                add(prefix + " gallery end key", () -> {
                    require(mc().screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0), "Gallery End was not consumed");
                    require((Integer) read("lootPage") > 0, "Gallery did not reveal later possible items");
                    assertFocusVisible();
                });
                add(prefix + " gallery last page capture", () -> capture(prefix + "-loot-gallery-last"));
                add(prefix + " Royal eligible tiers", () -> {
                    click((Button) read("lootBack")); click(((Button[]) read("lootPreviews"))[2]);
                    Button[] tiers = (Button[]) read("lootTiers");
                    require(!tiers[0].active && !tiers[1].active && tiers[2].active && tiers[3].active,
                            "Royal gallery exposes impossible lower tiers");
                    click(tiers[2]);
                    require((Integer) read("previewTier") == 2 && (Integer) read("lootPage") == 0,
                            "Rarity navigation failed to reset gallery page");
                });
                add(prefix + " rare gallery capture", () -> capture(prefix + "-loot-gallery-rare"));
                add(prefix + " return from gallery", () -> {
                    click((Button) read("lootBack"));
                    require(!(Boolean) read("showingLootGallery") && (Integer) read("confirmBox") == -1,
                            "Back did not clear gallery safely");
                    assertFocusVisible();
                    check("Possible loot gallery uses eligible native ItemStacks, keyboard paging and returns without a purchase at " + prefix);
                });
            }
            if (page == CoreCommandPage.DEFENSES) {
                add(prefix + " structure catalogue", () -> click("Place structure", "Structures"));
                add(prefix + " structure capture", () -> capture(prefix + "-building-structures"));
                for (DefenseBlueprint.Kind kind : DefenseBlueprint.Kind.values()) {
                    add(prefix + " select plan " + kind.name(), () -> {
                        Button[] plans = (Button[]) read("defensePlans");
                        int desired = kind.ordinal() / new CoreBuildingLayout(CoreHireLayout.fit(mc().screen.width, mc().screen.height)).plansPerPage();
                        for (int tries = 0; !plans[kind.ordinal()].visible && tries < DefenseBlueprint.Kind.values().length; tries++) {
                            click((Integer) read("planPage") > desired ? "‹" : "›");
                            assertFocusVisible();
                        }
                        require(plans[kind.ordinal()].visible, "Catalogue did not reveal " + kind);
                        click(plans[kind.ordinal()]);
                        require(read("selectedDefense") == kind, "Plan hitbox selected the wrong 3D preview");
                    });
                    add(prefix + " plan capture " + kind.name(), () -> capture(prefix + "-plan-" + slug(kind.name())));
                }
                add(prefix + " catalogue previous endpoint", () -> {
                    while ((Integer) read("planPage") > 0) { click("‹"); assertFocusVisible(); }
                    require((Integer) read("planPage") == 0, "Previous plans did not reach the first page");
                    check("Plan pager endpoint focus stays on a visible active control at " + prefix);
                });
                add(prefix + " construction report", () -> click("Construction"));
                add(prefix + " construction capture", () -> capture(prefix + "-building-construction"));
                add(prefix + " construction paging", () -> {
                    Button next = (Button) read("constructionNext");
                    require(next != null && next.visible && next.active, "Sample report should have a next page");
                    click(next); require((Integer) read("constructionPage") > 0, "Report next-page hitbox did not change page");
                    click((Button) read("constructionPrevious"));
                    require((Integer) read("constructionPage") == 0, "Report previous-page hitbox did not restore page");
                    check("Construction next/previous works at " + prefix);
                });
            }
            if (page == CoreCommandPage.INTEL) {
                add(prefix + " enemy lore", () -> click("Enemy Lore"));
                add(prefix + " lore capture", () -> capture(prefix + "-intel-enemy-lore"));
                add(prefix + " playbook", () -> click("How to Play"));
                add(prefix + " playbook capture", () -> capture(prefix + "-intel-how-to-play"));
            }
        }
    }

    private static void stateMatrix(String prefix) {
        add(prefix + " disabled Army states", () -> {
            selectPage(CoreCommandPage.ARMY);
            Button[] hire = (Button[]) read("hire");
            require(!hire[0].active && hire[0].getMessage().getString().equals("Hired"), "Hired offer not disabled");
            require(!hire[1].active && hire[1].getMessage().getString().startsWith("Need"), "Insufficient offer not disabled");
            require(!hire[2].active, "Unavailable offer not disabled");
            require(hire[3].active, "Affordable sample offer should be enabled without buying it");
            check("Hired, unavailable, insufficient and affordable sample hire states at " + prefix);
        });
        add(prefix + " empty Army", () -> openCore("loading/unavailable offers", false, false));
        add(prefix + " empty Army capture", () -> capture(prefix + "-army-unavailable"));
        add(prefix + " empty Treasury", () -> selectPage(CoreCommandPage.TREASURY));
        add(prefix + " empty Treasury capture", () -> capture(prefix + "-treasury-empty"));
        add(prefix + " loading report", () -> { selectPage(CoreCommandPage.DEFENSES); click("Construction"); });
        add(prefix + " loading report capture", () -> {
            require(!menu().constructionLoaded(), "Loading fixture unexpectedly has a report");
            capture(prefix + "-construction-loading");
        });
        add(prefix + " empty report", () -> { menu().construction(List.of()); fixture = "QA SAMPLE: empty report, not a server response"; });
        add(prefix + " empty report capture", () -> capture(prefix + "-construction-empty"));
        add(prefix + " civilian limit", () -> {
            openCore("civilian capacity sample", true, true); menu().setData(32, CivilianLedger.LIMIT);
            selectPage(CoreCommandPage.CIVILIANS);
        });
        add(prefix + " civilian limit capture", () -> {
            require(!((Button) read("civilianRecruit")).active, "Full civilian capacity must disable recruiting");
            capture(prefix + "-civilians-capacity");
        });
        add(prefix + " loot first confirmation", () -> {
            selectPage(CoreCommandPage.LOOT);
            // First click only. It selects local confirmation; the second click
            // would send a paid action, so that path is explicitly never invoked.
            click(((Button[]) read("boxes"))[0]);
            require((Integer) read("confirmBox") == 0, "First loot click did not request local confirmation");
        });
        add(prefix + " loot confirmation capture", () -> capture(prefix + "-loot-confirmation"));
        add(prefix + " loot confirmation cancelled by navigation", () -> {
            selectPage(CoreCommandPage.ARMY); selectPage(CoreCommandPage.LOOT);
            require((Integer) read("confirmBox") == -1, "Page switch retained a pending loot purchase");
            check("First-click loot confirmation is cleared by navigation without a purchase at " + prefix);
        });
    }

    private static void keyboardAndScroll(String prefix) {
        add(prefix + " focus Intel", () -> {
            selectPage(CoreCommandPage.INTEL); click("Units"); search().setValue(""); click(search());
            require(search().isFocused(), "Intel search did not acquire focus through its visible hitbox");
            focusWindow();
        });
        add(prefix + " native E press", () -> press(KeyEvent.VK_E));
        add(prefix + " native E release", () -> releaseKeys());
        add(prefix + " verify E remains search input", () -> {
            require(mc().screen instanceof CoreHireScreen && search().isFocused() && search().getValue().equals("e"),
                    "Actual E key closed inventory or failed to type into Intel search");
            check("Native OS E key remains typed Intel search text at " + prefix);
            capture(prefix + "-intel-keyboard-e");
        });
        add(prefix + " no-result search", () -> search().setValue("qa-no-such-unit-20261004"));
        add(prefix + " no-result search capture", () -> capture(prefix + "-intel-no-results"));
        add(prefix + " clear search", () -> { click("Clear"); require(search().getValue().isEmpty(), "Clear left a query"); });
        add(prefix + " Intel scroll", () -> {
            int x = (Integer) read("intelBodyX"), y = (Integer) read("intelBodyY");
            int width = (Integer) read("intelBodyW"), height = (Integer) read("intelBodyH");
            require((Integer) read("intelMaxOffset") > 0, "Units must overflow in compact viewport");
            require(mc().screen.mouseScrolled(x + width / 2.0, y + height / 2.0, -1), "Intel body rejected wheel");
            require((Integer) read("intelOffset") > 0, "Intel wheel did not scroll content");
            int offset = (Integer) read("intelOffset");
            click("Enemy Lore"); click("Units");
            require((Integer) read("intelOffset") == offset, "Intel section did not preserve its scroll position");
            check("Intel wheel scrolling and subsection scroll memory at " + prefix);
        });
        add(prefix + " scrolled capture", () -> capture(prefix + "-intel-scrolled"));
        add(prefix + " hold Ctrl", () -> press(KeyEvent.VK_CONTROL));
        add(prefix + " native Ctrl Tab", () -> press(KeyEvent.VK_TAB));
        add(prefix + " release Ctrl Tab", NativeHudQa::releaseKeys);
        add(prefix + " verify keyboard next-page", () -> {
            require(currentPage() == CoreCommandPage.ARMY, "Native Ctrl+Tab did not wrap Intel to Army");
            assertFocusVisible(); check("Native Ctrl+Tab wraps to Army without hidden focus at " + prefix);
        });
        add(prefix + " hold Ctrl Shift", () -> { press(KeyEvent.VK_CONTROL); press(KeyEvent.VK_SHIFT); });
        add(prefix + " native reverse Ctrl Tab", () -> press(KeyEvent.VK_TAB));
        add(prefix + " release reverse Ctrl Tab", NativeHudQa::releaseKeys);
        add(prefix + " verify reverse keyboard page", () -> {
            require(currentPage() == CoreCommandPage.INTEL, "Native Ctrl+Shift+Tab did not wrap Army to Intel");
            assertFocusVisible(); check("Native Ctrl+Shift+Tab wraps to Intel at " + prefix);
            capture(prefix + "-intel-keyboard-focus");
        });
        add(prefix + " escape", () -> press(KeyEvent.VK_ESCAPE));
        add(prefix + " release escape", NativeHudQa::releaseKeys);
        add(prefix + " verify escape", () -> { require(mc().screen == null, "Escape did not dismiss HUD"); check("Native Escape closes HUD at " + prefix); });
    }

    private static void secondaryMatrix(String prefix) {
        add(prefix + " Codex", () -> {
            fixture = "QA SAMPLE: client DashboardSnapshot; not a live siege or server report";
            mc().setScreen(new SiegeCommandScreen(dashboard()));
        });
        for (String tab : List.of("Core", "How to play", "Journal")) {
            add(prefix + " Codex " + tab, () -> click(tab));
            add(prefix + " Codex capture " + tab, () -> capture(prefix + "-codex-" + slug(tab)));
            if (tab.equals("How to play")) {
                add(prefix + " Codex guide navigation", () -> {
                    click("Next tip"); require((Integer) field(SiegeCommandScreen.class, "guideIndex") == 1, "Guide Next did not change tip");
                    click("Previous"); require((Integer) field(SiegeCommandScreen.class, "guideIndex") == 0, "Guide Previous did not restore tip");
                    AbstractWidget body = (AbstractWidget) field(SiegeCommandScreen.class, "guide");
                    click(body);
                    require(body.isFocused(), "Guide body failed to retain keyboard focus");
                    var focused = mc().screen.getFocused();
                    ((SiegeCommandScreen) mc().screen).updateSnapshot(dashboard());
                    require(mc().screen.getFocused() == focused, "Read-only Codex sync replaced focused body");
                    require(mc().screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0), "Guide End key was not consumed");
                    require(mc().screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0), "Guide Home key was not consumed");
                    check("Codex tip navigation, body keyboard handling and focus across sync at " + prefix);
                });
            }
            if (tab.equals("Journal")) {
                add(prefix + " Codex journal navigation", () -> {
                    click("Next"); require((Integer) field(SiegeCommandScreen.class, "journalPage") == 1, "Journal Next did not change page");
                    click("Previous"); require((Integer) field(SiegeCommandScreen.class, "journalPage") == 0, "Journal Previous did not restore page");
                    click("Close"); require(mc().screen == null, "Codex Close failed");
                    check("Codex Journal next/previous and Close hitboxes at " + prefix);
                });
            }
        }
        add(prefix + " settings", () -> {
            fixture = "Fresh disposable QA config defaults; no setting values edited";
            mc().setScreen(new SiegeOverhaulConfigScreen(null, RaidConfig.SPEC));
        });
        add(prefix + " settings capture", () -> capture(prefix + "-settings"));
        add(prefix + " settings no matches", () -> {
            EditBox filter = (EditBox) field(SiegeOverhaulConfigScreen.class, "filterBox");
            click(filter); filter.setValue("qa-no-such-setting-20261004");
        });
        add(prefix + " settings no-match capture", () -> capture(prefix + "-settings-no-results"));
        add(prefix + " settings restore and scroll", () -> {
            ((EditBox) field(SiegeOverhaulConfigScreen.class, "filterBox")).setValue("");
            require(mc().screen.mouseScrolled(mc().screen.width / 2.0, mc().screen.height / 2.0, -1), "Settings wheel was not consumed");
            require((Integer) field(SiegeOverhaulConfigScreen.class, "firstRow") > 0, "Settings wheel did not reveal later rows");
            check("Settings empty-filter state and real row scrolling at " + prefix);
        });
        add(prefix + " settings scrolled capture", () -> capture(prefix + "-settings-scrolled"));
        add(prefix + " hero visuals", () -> click("Hero visuals"));
        add(prefix + " hero visuals capture", () -> capture(prefix + "-hero-visuals"));
        add(prefix + " settings back", () -> {
            click("Done"); require(mc().screen instanceof SiegeOverhaulConfigScreen, "Hero visuals Done lost settings parent");
            click("Done"); require(mc().screen == null, "Settings Done did not return to game");
            check("Settings and Hero visuals return through visible Done controls at " + prefix);
        });
    }

    private static void inspectionMatrix(String prefix) {
        add(prefix + " native inspection", () -> {
            fixture = "QA SAMPLE: uncommissioned 15-block protected native marker; actual synchronized blueprint, no paid construction";
            require(clientInspection() != null, "Native inspection marker disappeared");
            mc().setScreen(new ProtectedConstructionScreen(clientInspection(), mc().player));
        });
        add(prefix + " inspection capture", () -> capture(prefix + "-native-inspection"));
        add(prefix + " inspection cancel back", () -> {
            click("Cancel job");
            require(mc().screen instanceof net.minecraft.client.gui.screens.ConfirmScreen, "Cancel did not show confirmation");
            click("No"); require(mc().screen instanceof ProtectedConstructionScreen, "Declining cancel did not return to native inspection");
            click("Close"); require(mc().screen == null, "Native inspection Close failed");
            check("Actual native inspection preview, cancel-back and Close at " + prefix);
        });
    }

    private static UUID setUpInspection(net.minecraft.server.level.ServerPlayer owner) throws ReflectiveOperationException {
        require(owner != null, "Actual integrated-server owner missing");
        var level = owner.serverLevel();
        var origin = owner.blockPosition().offset(4, 0, 4);
        var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation("workers", "builder"));
        require(type != null, "Actual native builder registry missing");
        var entity = type.create(level);
        require(entity instanceof net.minecraft.world.entity.Mob, "Actual native builder could not be created");
        var builder = (net.minecraft.world.entity.Mob) entity;
        builder.setPos(owner.getX() + 2, owner.getY(), owner.getZ() + 2);
        // Inspection-only setup, matching the existing native renderer fixture.
        // No work goals or completed blocks are supplied or claimed as gameplay.
        builder.setNoAi(true); builder.setPersistenceRequired();
        com.devfarinsky.siegeoverhaul.compat.WorkersBridge.enablePlayerJob(builder, owner.getUUID());
        require(level.addFreshEntity(builder), "Could not publish native inspection fixture builder");
        var blueprint = new net.minecraft.nbt.CompoundTag();
        blueprint.putInt("width", 4); blueprint.putInt("depth", 5); blueprint.putInt("height", 3); blueprint.putString("facing", "south");
        var cells = new net.minecraft.nbt.ListTag();
        for (int z = 0; z < 5; z++) for (int y = 0; y < 3; y++) {
            var cell = new net.minecraft.nbt.CompoundTag();
            cell.putInt("x", 0); cell.putInt("y", y); cell.putInt("z", z);
            cell.put("state", net.minecraft.nbt.NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState()));
            cells.add(cell);
        }
        blueprint.put("blocks", cells);
        var area = ProtectedConstructionAreas.TYPE.get().create(level);
        require(area != null, "Protected native registry entry missing");
        area.initialize(origin, owner.blockPosition().offset(1, 0, 1), owner.getUUID(), owner.getGameProfile().getName(),
                builder.getUUID(), 4, 5, 3, blueprint);
        area.initializeBlueprint(blueprint); area.initializeProjection(false);
        require(level.addFreshEntity(area), "Could not publish actual protected inspection marker");
        return area.getUUID();
    }

    private static ProtectedBuildArea clientInspection() {
        if (mc().level == null || inspectionArea == null) return null;
        for (var entity : mc().level.entitiesForRendering()) if (entity.getUUID().equals(inspectionArea) && entity instanceof ProtectedBuildArea area) return area;
        return null;
    }

    private static List<Map<String, Object>> blueprintEvidence() throws ReflectiveOperationException {
        List<Map<String, Object>> cards = new ArrayList<>();
        Button[] plans = (Button[]) read("defensePlans");
        for (DefenseBlueprint.Kind kind : DefenseBlueprint.Kind.values()) {
            Button plan = plans[kind.ordinal()];
            if (!plan.visible) continue;
            var shows = plan.getClass().getDeclaredMethod("showsThumbnail", int.class, int.class); shows.setAccessible(true);
            require((Boolean) shows.invoke(null, plan.getWidth(), plan.getHeight()), "Visible plan lost its actual 3D thumbnail: " + kind);
            var thumbnailField = plan.getClass().getDeclaredField("thumbnail"); thumbnailField.setAccessible(true);
            Object thumbnail = thumbnailField.get(plan);
            var blocksField = thumbnail.getClass().getDeclaredField("sourceBlocks"); blocksField.setAccessible(true);
            var blocks = (Map<?, ?>) blocksField.get(thumbnail);
            require(blocks.equals(DefenseBlueprint.create(kind, net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.SOUTH).blocks()),
                    "Plan thumbnail differs from the actual production blueprint: " + kind);
            cards.add(Map.of("plan", kind.name(), "caption", plan.getMessage().getString(), "blockModels", blocks.size(),
                    "width", plan.getWidth(), "height", plan.getHeight(), "sourceBlueprintMatches", true));
        }
        require(!cards.isEmpty(), "No native-block-model plan cards are visible");
        return cards;
    }

    private static List<String> portraitEvidence() throws ReflectiveOperationException {
        var cacheField = EntityPortrait.class.getDeclaredField("CACHE"); cacheField.setAccessible(true);
        var failedField = EntityPortrait.class.getDeclaredField("FAILED"); failedField.setAccessible(true);
        var cache = (Map<?, ?>) cacheField.get(null); var failed = (java.util.Set<?>) failedField.get(null);
        List<String> classes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            require(!failed.contains(i) && cache.get(i) instanceof net.minecraft.world.entity.LivingEntity,
                    "Army native entity portrait fell back to an item icon in slot " + i);
            classes.add(cache.get(i).getClass().getName());
        }
        return classes;
    }

    private static void openCore(String name, boolean offers, boolean report) {
        Minecraft mc = mc();
        CoreHireMenu menu = new CoreHireMenu(99, mc.player.getInventory());
        fixture = "QA SAMPLE: " + name + "; client data only, no live faction/core or server transaction";
        menu.details("QA SAMPLE · " + name, offers ? List.of("Online  QA owner", "Online  QA scout", "Offline  QA builder",
                "Offline  QA archer", "Online  QA guard", "Offline  QA farmer", "Online  QA miner", "Offline  QA courier",
                "Online  QA smith", "Offline  QA captain", "Online  QA recruit", "Offline  QA veteran") : List.of(),
                offers ? new int[]{64, -16, 100, -32} : new int[0]);
        if (offers) {
            int[] roles = {0, 2, 7, 10}, costs = {16, 1000, -1, 50};
            for (int i = 0; i < 4; i++) { menu.setData(i, roles[i]); menu.setData(i + 4, costs[i]); }
            menu.setData(8, 1); menu.setData(9, 123); menu.setData(10, 1);
            menu.setData(14, 17); menu.setData(18, 480); menu.setData(20, 50);
            menu.setData(21, 7); menu.setData(23, 96); menu.setData(25, 1); menu.setData(26, 6);
            menu.setData(29, 5); menu.setData(30, 12400); menu.setData(32, 12); menu.setData(33, 24); menu.setData(35, 120);
            // Real vanilla item stacks, placed only in the client display container.
            for (int i = 0; i < 4; i++) {
                // CoreOfferEquipment.SLOTS order: head/chest/legs/feet/offhand/mainhand.
                var kit = new net.minecraft.world.item.Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE,
                        Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.SHIELD,
                        i == 1 ? Items.BOW : i == 2 ? Items.IRON_AXE : Items.IRON_SWORD};
                for (int slot = 0; slot < kit.length; slot++) menu.slots.get(6 + i * 6 + slot).set(new ItemStack(kit[slot]));
            }
        }
        if (report) {
            List<ConstructionReport.Job> jobs = new ArrayList<>();
            for (int i = 0; i < 6; i++) jobs.add(new ConstructionReport.Job("QA sample project " + (i + 1),
                    i == 5 ? 100 : i * 15, i == 5 ? "Complete (sample)" : "Partial progress (sample)", "No live marker (sample)",
                    i == 5 ? "All blocks verified (sample only)" : "Paused: awaiting supplies (sample)", "48 cobblestone (sample)",
                    UUID.nameUUIDFromBytes(("hud-only-project-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    1, "2 / 6 sections (sample)\nOne-time fee paid (sample only)", false, i == 5));
            menu.construction(jobs);
        }
        mc.setScreen(new CoreHireScreen(menu, mc.player.getInventory(), Component.literal("QA SAMPLE HUD")));
    }

    private static RaidEvents.DashboardSnapshot dashboard() {
        List<RaidEvents.JournalRow> journal = new ArrayList<>();
        for (int i = 0; i < 12; i++) journal.add(new RaidEvents.JournalRow(24000L * (12 - i), "wilds_marauders",
                "QA SAMPLE host " + (i + 1), "", 3, 3, "victory", 64));
        return new RaidEvents.DashboardSnapshot("QA SAMPLE faction", true, true, "QA SAMPLE stronghold",
                3, 5, 12, 4, 8, 20, false, 0, 8, 3, 1, 2, 4, 0,
                "QA SAMPLE gate", 0, "QA SAMPLE cooldown", 64, true,
                "wilds_marauders", "", "QA SAMPLE opening", "QA SAMPLE chant", "North (sample)", 128,
                "Next wave (sample)", "4 recruits (sample)", 12, "Prepared (sample)", "8 recruits (sample)",
                "Display fixture only", List.of(), List.of(), journal, true, "QA SAMPLE claim", true, true, true, true);
    }

    private static void selectPage(CoreCommandPage target) throws Exception {
        require(mc().screen instanceof CoreHireScreen, "Core screen missing for page navigation");
        // Reach hidden tabs through the actual overflow-arrow hitbox, then click
        // that page's visible tab. No direct mutation of screen navigation state.
        for (int attempt = 0; attempt < CoreCommandPage.values().length; attempt++) {
            Button[] tabs = (Button[]) read("pageButtons");
            if (tabs[target.ordinal()].visible) {
                click(tabs[target.ordinal()]);
                require(currentPage() == target, "Visible tab did not select " + target);
                assertFocusVisible(); return;
            }
            click((Button) read("nextPage"));
        }
        throw new AssertionError("Could not reveal actual page tab " + target);
    }

    private static void click(String... labels) {
        require(mc().screen != null, "No screen for click");
        for (var child : mc().screen.children()) if (child instanceof AbstractWidget widget && widget.visible && widget.active)
            for (String label : labels) if (widget.getMessage().getString().equals(label)) { click(widget); return; }
        throw new AssertionError("Visible active control missing: " + String.join(" / ", labels));
    }

    private static void click(AbstractWidget widget) {
        Screen screen = mc().screen;
        float scale = screen instanceof CoreHireScreen ? CoreHireLayout.fit(screen.width, screen.height).scale() : 1;
        double x = (widget.getX() + widget.getWidth() / 2.0) * scale;
        double y = (widget.getY() + widget.getHeight() / 2.0) * scale;
        require(widget.visible && widget.active && x >= 0 && x < screen.width && y >= 0 && y < screen.height,
                "Control is hidden, disabled or outside actual viewport: " + widget.getMessage().getString());
        require(screen.mouseClicked(x, y, 0), "Actual hitbox rejected click: " + widget.getMessage().getString());
        screen.mouseReleased(x, y, 0);
    }

    private static void assertFocusVisible() {
        if (mc().screen.getFocused() instanceof AbstractWidget widget)
            require(widget.visible && widget.active, "Keyboard focus remained on a hidden/disabled widget");
    }

    private static List<Map<String, Object>> geometry() {
        Screen screen = mc().screen;
        require(screen != null, "No actual screen to capture");
        float scale = screen instanceof CoreHireScreen ? CoreHireLayout.fit(screen.width, screen.height).scale() : 1;
        List<AbstractWidget> widgets = new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            double x = widget.getX() * scale, y = widget.getY() * scale;
            double w = widget.getWidth() * scale, h = widget.getHeight() * scale;
            require(w > 0 && h > 0 && x >= 0 && y >= 0 && x + w <= screen.width + .1 && y + h <= screen.height + .1,
                    "Visible widget is clipped: " + widget.getMessage().getString() + " " + x + "," + y + " " + w + "x" + h);
            if (widget.active) require(widget.isMouseOver(widget.getX() + widget.getWidth() / 2.0,
                    widget.getY() + widget.getHeight() / 2.0), "Widget center is outside its hitbox");
            for (AbstractWidget other : widgets) require(widget.getX() >= other.getX() + other.getWidth()
                            || other.getX() >= widget.getX() + widget.getWidth()
                            || widget.getY() >= other.getY() + other.getHeight()
                            || other.getY() >= widget.getY() + widget.getHeight(),
                    "Visible hitboxes overlap: " + widget.getMessage().getString() + " / " + other.getMessage().getString());
            widgets.add(widget);
            result.add(Map.of("label", widget.getMessage().getString(), "x", x, "y", y, "width", w, "height", h,
                    "active", widget.active, "focused", widget.isFocused()));
        }
        require(!widgets.isEmpty(), "Captured screen has no visible controls");
        assertFocusVisible(); return result;
    }

    private static void capture(String name) {
        capture = name + ".png"; captureFrame = frame + 3; captureStarted = System.nanoTime();
        focusWindow();
        GLFW.glfwSetCursorPos(mc().getWindow().getWindow(), 32, 32);
        GLFW.glfwSetCursorPos(mc().getWindow().getWindow(), 2, 2);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        frame++;
        if (capture == null || frame < captureFrame) return;
        Minecraft mc = mc();
        try (NativeImage pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            require(pixels.getWidth() == requestedWidth && pixels.getHeight() == requestedHeight, "Framebuffer size differs from settled viewport");
            int first = pixels.getPixelRGBA(0, 0), changed = 0;
            for (int y = 0; y < pixels.getHeight(); y += 16)
                for (int x = 0; x < pixels.getWidth(); x += 16)
                    if (pixels.getPixelRGBA(x, y) != first) changed++;
            require(changed > 50, "Actual framebuffer appears blank");
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("screenshot", capture); view.put("fixture", fixture); view.put("viewport", viewport(mc));
            view.put("screenClass", mc.screen.getClass().getName()); view.put("widgets", geometry());
            if (mc.screen instanceof ProtectedConstructionScreen) {
                long nativePreviews = mc.screen.children().stream().filter(child -> child instanceof com.talhanation.workers.client.gui.structureRenderer.StructurePreviewWidget).count();
                require(nativePreviews == 1, "Actual native inspection preview is missing");
                view.put("nativeStructurePreviewCount", nativePreviews);
                require(clientInspection().getStructureNBT().getList("blocks", 10).size() == 15,
                        "Actual synchronized native inspection blueprint differs from its labeled sample");
                view.put("nativeBlueprintSampleBlocks", 15);
            }
            view.put("nonblankSamples", changed);
            if (mc.screen instanceof CoreHireScreen) {
                view.put("page", currentPage().name());
                if (currentPage() == CoreCommandPage.ARMY && menu().role(0) >= 0) {
                    view.put("nativePortraits", portraitEvidence());
                    view.put("portraitLogicalSize", CoreHireLayout.fit(mc.screen.width, mc.screen.height).hirePortraitSize());
                }
                if (currentPage() == CoreCommandPage.TERRITORY) {
                    Button[] upgrades = (Button[]) read("territoryBuffs");
                    for (int i : new int[]{0, 1}) require(!upgrades[i].active
                                    && upgrades[i].getMessage().getString().equals("Unavailable"),
                            "Unimplemented territory upgrade remains purchasable or claims active");
                    require(menu().territoryBuffMask() == 5 && menu().bank() == 480,
                            "Disabled-state rendering changed illustrative ownership or funds");
                    int active = com.devfarinsky.siegeoverhaul.core.TerritoryBuffs.activeCount(menu().territoryBuffMask());
                    int retained = com.devfarinsky.siegeoverhaul.core.TerritoryBuffs.retainedCount(menu().territoryBuffMask());
                    require(active == 1 && retained == 1, "Unavailable owned effect was counted as active");
                    view.put("territoryAvailability", Map.of("ownershipMask", menu().territoryBuffMask(),
                            "active", active, "retained", retained, "unavailableButtons", 2));
                    check("Unavailable territory upgrades stay disabled and preserve sample ownership at " + capture);
                }
                if (currentPage() == CoreCommandPage.CIVILIANS) {
                    var layout = (CoreHireLayout) read("layout");
                    int height = layout.contentBottom() - layout.contentY() - 30;
                    int portrait = Math.min(layout.compact() ? 48 : 96, Math.max(24, height - 28));
                    int lineBudget = Math.max(0, (height - Math.max(portrait + 18, 65) - 7) / 10);
                    var guidanceMethod = CoreHireScreen.class.getDeclaredMethod("civilianGuidance", boolean.class);
                    guidanceMethod.setAccessible(true);
                    String guidance = (String) guidanceMethod.invoke(null, layout.compact());
                    int lineCount = mc.font.split(Component.literal(guidance), layout.width() - 40).size();
                    require(lineCount <= lineBudget, "Civilian guidance cuts off before its complete final sentence");
                    view.put("civilianGuidance", guidance);
                    view.put("civilianGuidanceLines", lineCount); view.put("civilianGuidanceLineBudget", lineBudget);
                }
                if (currentPage() == CoreCommandPage.DEFENSES && read("buildingSection").toString().equals("STRUCTURES")) {
                    view.put("selectedPlan", read("selectedDefense").toString());
                    view.put("nativeBlueprintCards", blueprintEvidence());
                }
                view.put("buildingSection", read("buildingSection").toString()); view.put("intelSection", read("intelSection"));
            }
            pixels.writeToFile(evidence.resolve(capture)); SHOTS.add(capture); VIEWS.add(view); capture = null;
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void initialize(Minecraft mc) throws Exception {
        // A startup failure must still identify its mode and fixture scope.
        REPORT.put("mode", "hud"); REPORT.put("startedUtc", Instant.now().toString());
        REPORT.put("coverage", "Actual Minecraft frames, fonts, sprites and production screen widgets in a fresh isolated world. Labeled client-menu and dashboard samples only; not server-generated transactions or reports.");
        REPORT.put("notCovered", List.of("Paid actions or server authorization/payment/reward state", "Real faction claims, construction or native worker AI",
                "Dedicated-server connection", "Resource-pack, shader, localization, screen-reader and physical GPU matrix",
                "OS mouse routing: navigation uses actual Screen.mouseClicked hitboxes; keyboard uses native OS input"));
        String configured = System.getProperty("siegeoverhaul.nativeQa.directory", "");
        require(!configured.isBlank(), "Missing isolated HUD QA directory");
        directory = Path.of(configured).toAbsolutePath().normalize();
        require(directory.endsWith(Path.of("build", "native-hud-qa", "client")), "Unsafe HUD game directory");
        require(mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()), "Unexpected active game directory");
        evidence = directory.getParent().resolve("evidence"); Files.createDirectories(evidence);
        require(!Files.exists(evidence.resolve("result.json")), "Refusing to overwrite existing HUD evidence");
        require(!mc.getWindow().isFullscreen(), "HUD QA requires its own windowed client");
        mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(4); mc.options.simulationDistance().set(5);
        Map<String, String> mods = new LinkedHashMap<>(); Map<String, Object> artifacts = new LinkedHashMap<>();
        for (String id : List.of("minecraft", "forge", "siegeoverhaul", "workers", "recruits", "smallships", "siegeweapons")) {
            var container = ModList.get().getModContainerById(id);
            require(container.isPresent(), "Required actual mod missing: " + id);
            var info = container.orElseThrow().getModInfo(); mods.put(id, info.getVersion().toString());
            if (List.of("workers", "recruits", "smallships", "siegeweapons").contains(id)) {
                Path jar = info.getOwningFile().getFile().getFilePath(); require(Files.isRegularFile(jar), "Loaded companion JAR missing: " + id);
                artifacts.put(id, Map.of("fileName", jar.getFileName().toString(), "sha256", sha256(jar),
                        "kind", "ForgeGradle remapped development runtime JAR, not original release bytes"));
            }
        }
        for (var expected : Map.of("minecraft", "1.20.1", "forge", "47.4.16", "workers", "2.0.3", "recruits", "1.15.2",
                "smallships", "2.0.0-b1.4", "siegeweapons", "0.2.5").entrySet())
            require(expected.getValue().equals(mods.get(expected.getKey())), "Unreviewed runtime version: " + expected.getKey());
        REPORT.put("loadedModVersions", mods); REPORT.put("loadedCompanionArtifacts", artifacts);
        REPORT.put("openGlVendor", GL11.glGetString(GL11.GL_VENDOR)); REPORT.put("openGlRenderer", GL11.glGetString(GL11.GL_RENDERER));
        REPORT.put("openGlVersion", GL11.glGetString(GL11.GL_VERSION));
        check("All four pinned real companion mods loaded in isolated HUD runtime");
        initializeKeyboard(mc);
    }

    private static void initializeKeyboard(Minecraft mc) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        REPORT.put("nativeKeyboard", input);
        input.put("backend", "java.awt.Robot native platform input queue");
        input.put("ready", false);
        input.put("headlessPropertyBefore", System.getProperty("java.awt.headless", "<unset>"));
        require(ENABLED && mc.screen instanceof TitleScreen && mc.level == null && mc.getSingleplayerServer() == null,
                "Native HUD input must initialize in its isolated title screen, before world creation");
        long window = mc.getWindow().getWindow();
        require(window != 0 && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE
                        && mc.getWindow().getWidth() > 0 && mc.getWindow().getHeight() > 0,
                "Native HUD input requires its actual visible GLFW window");
        String display = System.getenv("DISPLAY");
        boolean linux = System.getProperty("os.name", "").startsWith("Linux");
        input.put("xDisplayPresent", display != null && !display.isBlank());
        require(!linux || display != null && !display.isBlank(), "Native HUD input requires the existing X display on Linux");

        // Official Minecraft 1.20.1 client SHA1 0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838:
        // Main.<clinit> sets java.awt.headless=true before main constructs Minecraft.
        // A JVM launch flag is therefore overwritten. Vanilla client startup uses
        // GLFW; the server favicon's AWT image path is later than this title-screen
        // setup. Reset this JVM-only hint BEFORE this fixture first queries AWT.
        // GraphicsEnvironment lazily caches it on Java 17. If Forge/a companion
        // already initialized AWT headless, the check below fails; never reflectively
        // reset its cache, weaken permission checks, or substitute screen key calls.
        System.setProperty("java.awt.headless", "false");
        boolean headless = java.awt.GraphicsEnvironment.isHeadless();
        input.put("headlessPropertyAfter", System.getProperty("java.awt.headless"));
        input.put("graphicsEnvironmentHeadless", headless);
        require(!headless, "AWT initialized headless before HUD setup; refusing to replace cached AWT state");
        keyboard = new Robot(); keyboard.setAutoDelay(0);
        input.put("ready", true);
        check("Actual display-backed Robot initialized before world creation; native keyboard assertions remain required");
    }

    private static void requestViewport(int width, int height, int scale) {
        requestedWidth = width; requestedHeight = height; requestedScale = scale;
        GLFW.glfwRestoreWindow(mc().getWindow().getWindow()); mc().getWindow().setWindowed(width, height);
        resizeStarted = System.nanoTime(); resizing = true;
    }
    private static Map<String, Object> viewport(Minecraft mc) {
        var window = mc.getWindow();
        return Map.of("framebufferWidth", window.getWidth(), "framebufferHeight", window.getHeight(),
                "windowWidth", window.getScreenWidth(), "windowHeight", window.getScreenHeight(),
                "guiWidth", window.getGuiScaledWidth(), "guiHeight", window.getGuiScaledHeight(), "requestedGuiScale", mc.options.guiScale().get());
    }
    private static void focusWindow() { GLFW.glfwFocusWindow(mc().getWindow().getWindow()); }
    private static void press(int key) { keyboard.keyPress(key); HELD_KEYS.add(key); }
    private static void releaseKeys() {
        for (int i = HELD_KEYS.size() - 1; i >= 0; i--) keyboard.keyRelease(HELD_KEYS.get(i));
        HELD_KEYS.clear();
    }
    /** Read-only diagnostics; the fixture never assigns production private state. */
    private static Object read(String name) throws ReflectiveOperationException {
        return field(CoreHireScreen.class, name);
    }
    private static Object field(Class<?> owner, String name) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(mc().screen);
    }
    private static CoreCommandPage currentPage() throws ReflectiveOperationException { return (CoreCommandPage) read("tab"); }
    private static EditBox search() throws ReflectiveOperationException { return (EditBox) read("intelSearch"); }
    private static CoreHireMenu menu() { return ((CoreHireScreen) mc().screen).getMenu(); }
    private static Minecraft mc() { return Minecraft.getInstance(); }
    private static String slug(String label) { return label.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'); }
    private static void add(String name, CheckedAction action) { STEPS.add(new Step(name, action)); }
    private static void check(String name) { CHECKS.add(name); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static String sha256(Path file) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536]; int size;
            while ((size = stream.read(buffer)) != -1) digest.update(buffer, 0, size);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private static void finish(Minecraft mc, Throwable failure) {
        if (finished) return;
        finished = true; releaseKeys();
        REPORT.put("status", failure == null ? "passed" : "failed"); REPORT.put("finishedUtc", Instant.now().toString());
        REPORT.put("completedSteps", step); REPORT.put("plannedSteps", STEPS.size());
        REPORT.put("assertions", CHECKS); REPORT.put("screenshots", SHOTS); REPORT.put("views", VIEWS);
        if (failure != null) {
            REPORT.put("failure", failure.toString()); FactionLogger.LOG.error("Native HUD QA failed at step {}", step, failure);
            if (evidence != null) try (NativeImage pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                pixels.writeToFile(evidence.resolve("failure-frame.png"));
            } catch (Throwable ignored) { }
        }
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception writeFailure) { FactionLogger.LOG.error("Could not write HUD evidence", writeFailure); }
        FactionLogger.LOG.info("Native HUD QA {}: {} actual framebuffers", failure == null ? "passed" : "failed", SHOTS.size());
        mc.stop();
    }
}
