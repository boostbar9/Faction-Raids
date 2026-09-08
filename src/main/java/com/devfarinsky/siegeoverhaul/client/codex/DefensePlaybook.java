package com.devfarinsky.siegeoverhaul.client.codex;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Curated defense-strategy tips displayed in the Warlord's Codex.
 * Purely static; the book renders these as tip cards. The content is written
 * as concrete, actionable advice \u2014 no generic "build walls" filler.
 *
 * <p>v2.28.0 audit: the ravager, wooden-door trap, wave-rest, and
 * reward-eligibility tips were rewritten to remove claims the mod does not
 * back with code (there is no obsidian trap system, no configurable rest
 * between waves, ravagers do not break walls, and the reward-eligible
 * indicator only shows on the pre-siege forecast card).
 */
@OnlyIn(Dist.CLIENT)
public final class DefensePlaybook {

    public static final List<Tip> TIPS = List.of(
            new Tip("Setup","Claim land. Place your core.","Create or join a Recruits faction. Claim land in the Overworld, then place one Siege Core inside it. Enemies target that core."),
            new Tip("Hire","Build your army at the core","Right-click the core for two recruit offers, one worker and a featured hero. Shared faction stock rotates every 15 minutes. Prices are shown before hiring."),
            new Tip("Defend","Keep troops beside the core","Enemies capture by outnumbering you and your recruits in the core ring. Equal numbers pause progress; a defending majority reverses it. Walls buy time."),
            new Tip("Recapture","Take your territory back","After capture, gather at the same core and outnumber its occupiers until recapture completes. The claim returns to your faction. Capture settings are configurable."),
            new Tip("Counterattack","Strike the enemy camp","Builders expand the foothold while guards hold their posts. Disrupt preparations or defeat siege engineers, but leave a reserve to protect your core.")
    );

    private DefensePlaybook() {}

    public record Tip(String tag, String title, String body) {}
}
