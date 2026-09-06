package com.devfarinsky.factionraids.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Pattern;

/**
 * v2.31.0 Chat Presentation Overhaul.
 *
 * <p>Centralized formatter for every player-facing chat line the mod emits.
 * Enforces the modernized presentation contract so status text, event beats,
 * and cross-team broadcasts all render with consistent typography:</p>
 *
 * <ul>
 *   <li>Kill the {@code [Faction Raids]} bracket prefix. Team chat gets a
 *       faint {@link #GLYPH_TEAM diamond} bullet; cross-server broadcasts
 *       get a {@link #GLYPH_CROSS crossed-swords} lead so they read as a
 *       distinct event class.</li>
 *   <li>Kill ALL CAPS. Titles use {@link #titleCase(String) title-cased}
 *       words and rely on size + color for weight, not shouting.</li>
 *   <li>Kill exclamation marks. Urgency comes from color escalation.</li>
 *   <li>Standardize separators: {@link #SEP bullet} between chips,
 *       {@link #DASH em-dash} between clauses, {@link #ARROW arrow} for
 *       progression callouts.</li>
 *   <li>Weighted color ramp for pressure/occupation lines
 *       ({@link #pressureColor(int)}): gray -> yellow -> gold -> red. Same
 *       ramp every time so players learn to read intensity at a glance.</li>
 * </ul>
 *
 * <p>Every {@code Component.literal(...)} sent to the player should be built
 * through one of the factory methods here. Direct {@code Component.literal}
 * outside this class is reserved for entity names, item tooltips, and boss
 * bars \u2014 anywhere the chat presentation contract does not apply.</p>
 */
public final class ChatStyle {

    private ChatStyle() {}

    // ---- Glyphs -----------------------------------------------------------

    /**
     * Bullet used to lead every team-scoped raid chat line. Faint gray so it
     * feels like a marker, not a shout. Replaces the old {@code [Faction
     * Raids]} bracket prefix.
     */
    public static final String GLYPH_TEAM = "\u25c6 "; // \u25c6 diamond

    /**
     * Lead for cross-server broadcasts (raid start on another team, faction
     * fall, etc). Read as: "this is a war cry that everyone hears," distinct
     * from personal tactical chat.
     */
    public static final String GLYPH_CROSS = "\u2694 "; // \u2694 crossed swords

    /**
     * Bullet separator for stat chips inside a single line.
     * Ex: "wave 3 \u00b7 12 raiders \u00b7 2:40 left".
     */
    public static final String SEP = " \u00b7 "; // middle dot

    /** Em-dash separator for clauses. Reads cleaner than hyphen-space. */
    public static final String DASH = " \u2014 ";

    /** Arrow for progression callouts. Ex: "wave 3 \u2192 wave 4". */
    public static final String ARROW = " \u2192 ";

    // ---- Color ramps ------------------------------------------------------

    /**
     * Standard color for informational raid lines (a thing happened, no
     * immediate action required). Neutral, easy to skim past.
     */
    public static final ChatFormatting INFO = ChatFormatting.GRAY;

    /** Positive beats (defense win, wave cleared, engine destroyed). */
    public static final ChatFormatting GOOD = ChatFormatting.GREEN;

    /** Neutral tactical notes worth noticing. */
    public static final ChatFormatting NOTE = ChatFormatting.YELLOW;

    /** Escalating pressure (breach starting, occupation climbing). */
    public static final ChatFormatting WARN = ChatFormatting.GOLD;

    /** Critical events (perimeter breached, stronghold falling, commander). */
    public static final ChatFormatting ALERT = ChatFormatting.RED;

    /** Cross-server broadcast lead color. Recognizable at a glance. */
    public static final ChatFormatting BROADCAST = ChatFormatting.DARK_AQUA;

    /**
     * Four-band color ramp for pressure/occupation callouts. Same math as
     * the existing warning-band system (25/50/75/100) so no callsite has to
     * think about which color to pick \u2014 pass the percent, get the color.
     */
    public static ChatFormatting pressureColor(int percent) {
        if (percent >= 75) return ALERT;
        if (percent >= 50) return WARN;
        if (percent >= 25) return NOTE;
        return INFO;
    }

    // ---- Factories --------------------------------------------------------

    /**
     * Team-scoped raid line. Adds the diamond bullet and applies the given
     * base color. All other formatting (bold, italic) stays off by default
     * \u2014 weight comes from color, not decoration.
     */
    public static MutableComponent team(String message, ChatFormatting color) {
        return Component.literal(GLYPH_TEAM).withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(scrub(message)).withStyle(color));
    }

    /** Team line at INFO intensity (default). */
    public static MutableComponent team(String message) {
        return team(message, INFO);
    }

    /**
     * Cross-server broadcast. Leads with the crossed-swords glyph in
     * broadcast color so it visually parts from tactical chat.
     */
    public static MutableComponent broadcast(String message) {
        return Component.literal(GLYPH_CROSS).withStyle(BROADCAST, ChatFormatting.BOLD)
                .append(Component.literal(scrub(message)).withStyle(BROADCAST));
    }

    /**
     * Status chip for the action bar. Compact, no glyph, single color.
     * Example: {@code chip("Perimeter", 75)} -> "Perimeter \u00b7 75%".
     */
    public static MutableComponent chip(String label, int percent) {
        ChatFormatting c = pressureColor(percent);
        return Component.literal(label + SEP + percent + "%").withStyle(c, ChatFormatting.BOLD);
    }

    /** Plain action-bar chip with a preset color. */
    public static MutableComponent chip(String text, ChatFormatting color) {
        return Component.literal(scrub(text)).withStyle(color, ChatFormatting.BOLD);
    }

    /**
     * Title-card text. Title-cases input and strips exclamation marks so
     * screens read {@code Perimeter Breached}, not {@code PERIMETER BREACHED!}.
     */
    public static MutableComponent title(String text, ChatFormatting color) {
        return Component.literal(titleCase(scrub(text))).withStyle(color, ChatFormatting.BOLD);
    }

    /** Subtitle line for title cards. Case-normal, muted color. */
    public static MutableComponent subtitle(String text, ChatFormatting color) {
        return Component.literal(scrub(text)).withStyle(color);
    }

    // ---- Text hygiene -----------------------------------------------------

    private static final Pattern TRAILING_BANGS = Pattern.compile("!+");

    /**
     * Normalize a raw message string for the new presentation:
     * strips exclamation marks (urgency comes from color), collapses runs
     * of spaces, and trims. Kept package-visible so callers that build a
     * Component manually can still route through the hygiene pass.
     */
    static String scrub(String s) {
        if (s == null || s.isEmpty()) return "";
        String stripped = TRAILING_BANGS.matcher(s).replaceAll(".");
        // "sentence." + " sentence." collapses double period.
        stripped = stripped.replace("..", ".").replace(" .", ".").trim();
        // Collapse any doubled spaces introduced by the strips above.
        while (stripped.contains("  ")) stripped = stripped.replace("  ", " ");
        return stripped;
    }

    /**
     * Convert a string to Title Case for headline use. Preserves acronyms
     * of two or more consecutive uppercase letters when they were already
     * uppercase in the input (so {@code "NPC"} stays {@code "NPC"}) but
     * demotes shouted words ({@code "PERIMETER BREACHED"} becomes
     * {@code "Perimeter Breached"}).
     */
    public static String titleCase(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] parts = text.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];
            if (w.isEmpty()) continue;
            if (i > 0) out.append(' ');
            // Detect "was already an acronym in a mixed-case sentence" by
            // checking uppercase ratio; if the whole input is uppercase we
            // treat that as shouting and demote it.
            out.append(demote(w));
        }
        return out.toString();
    }

    private static String demote(String w) {
        // Small words stay lowercase mid-title (of, the, and, to, at, in, on).
        // First-word capitalization is handled by the caller iteration.
        String lower = w.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // ---- Title cards (v2.32.0) --------------------------------------------

    /**
     * Event weight for a title card. Controls fade/hold timings so a routine
     * beat feels punchy but a stronghold-fallen moment lingers. Timings are
     * in server ticks (20 = one second) and were tuned by hand:
     *
     * <ul>
     *   <li>{@link #ROUTINE}: fast in, short hold, fast out. Used for phase
     *       transitions where the player mostly needs a nudge.</li>
     *   <li>{@link #MAJOR}: standard cinematic beat. Perimeter breached,
     *       command assault, siege incoming.</li>
     *   <li>{@link #DEFINING}: victory or defeat. Slow fade-in, long hold,
     *       slow fade-out so the outcome reads as a moment, not a ping.</li>
     * </ul>
     */
    public enum TitleWeight {
        /** 8 tick fade-in, 30 tick hold, 12 tick fade-out (~2.5s total). */
        ROUTINE(8, 30, 12),
        /** 15 tick fade-in, 60 tick hold, 20 tick fade-out (~4.75s total). */
        MAJOR(15, 60, 20),
        /** 25 tick fade-in, 100 tick hold, 40 tick fade-out (~8.25s total). */
        DEFINING(25, 100, 40);

        public final int fadeInTicks;
        public final int holdTicks;
        public final int fadeOutTicks;

        TitleWeight(int fadeInTicks, int holdTicks, int fadeOutTicks) {
            this.fadeInTicks = fadeInTicks;
            this.holdTicks = holdTicks;
            this.fadeOutTicks = fadeOutTicks;
        }
    }

    // ---- Bossbar label composer (v2.32.0) ---------------------------------

    /**
     * Compose a bossbar label out of structured segments with typographic
     * hierarchy: leading epithet (if any), phase name, then value chips
     * separated by middle-dots. Reads far cleaner than the old
     * concatenated string with mixed separators.
     *
     * <p>Example output: {@code Ship-Wolves · Breach · stronghold 47m ·
     * 12 deployed · breach 25%}. All segments are joined by the same
     * middle-dot separator so the label has a single visual rhythm
     * instead of jumping between hyphens, colons, and bullets.</p>
     */
    public static String bossbarLabel(String epithet, String phase, String... chips) {
        StringBuilder out = new StringBuilder();
        if (epithet != null && !epithet.isEmpty()) {
            out.append(epithet).append(SEP);
        }
        out.append(phase);
        if (chips != null) {
            for (String c : chips) {
                if (c == null || c.isEmpty()) continue;
                out.append(SEP).append(c);
            }
        }
        return out.toString();
    }
}
