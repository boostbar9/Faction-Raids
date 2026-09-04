package com.devfarinsky.factionraids.command;

import com.devfarinsky.factionraids.RaidEvents;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;

/**
 * Builds the {@code /factionraids} Brigadier command tree.
 *
 * <p>Extracted from {@code RaidEvents.registerCommands} so adding, renaming, or
 * permission-gating a subcommand no longer requires editing a 2,000+ line file.
 * Individual handlers currently live as package-visible static methods on
 * {@code RaidEvents}; the intent is to migrate them into dedicated
 * {@code Handlers} classes (anchor, territory, member, admin, ...) as follow-up
 * work without needing to touch this file's shape.
 */
public final class RaidCommands {
    private RaidCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("factionraids")
                .executes(ctx -> RaidEvents.openDashboardCmd(ctx.getSource()))
                .then(Commands.literal("menu")
                        .executes(ctx -> RaidEvents.openDashboardCmd(ctx.getSource())))
                .then(Commands.literal("anchor")
                        .then(Commands.literal("set")
                                .executes(ctx -> RaidEvents.setAnchorCmd(ctx.getSource())))
                        .then(Commands.literal("claim")
                                .executes(ctx -> RaidEvents.claimLegacyAnchorCmd(ctx.getSource())))
                        .then(Commands.literal("remove")
                                .executes(ctx -> RaidEvents.removeAnchorCmd(ctx.getSource()))))
                .then(Commands.literal("home")
                        .then(Commands.literal("automatic")
                                .executes(ctx -> RaidEvents.enableAutomaticHomeCmd(ctx.getSource())))
                        .then(Commands.literal("refresh")
                                .executes(ctx -> RaidEvents.refreshAutomaticHomeCmd(ctx.getSource()))))
                .then(Commands.literal("territory")
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> RaidEvents.addDefensePointCmd(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> RaidEvents.removeDefensePointCmd(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> RaidEvents.listDefensePointsCmd(ctx.getSource()))))
                .then(Commands.literal("member")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> RaidEvents.addMemberCmd(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> RaidEvents.removeMemberCmd(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> RaidEvents.listMembersCmd(ctx.getSource()))))
                .then(Commands.literal("start")
                        .executes(ctx -> RaidEvents.startOwnRaidCmd(ctx.getSource(), null))
                        .then(Commands.argument("point", StringArgumentType.word())
                                .executes(ctx -> RaidEvents.startOwnRaidCmd(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "point")))))
                .then(Commands.literal("status")
                        .executes(ctx -> RaidEvents.statusCmd(ctx.getSource())))
                .then(Commands.literal("debug")
                        .executes(ctx -> RaidEvents.debugCmd(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> RaidEvents.helpCmd(ctx.getSource())))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2))
                        .executes(ctx -> RaidEvents.stopOwnRaidCmd(ctx.getSource())))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(ctx -> RaidEvents.adminListCmd(ctx.getSource())))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> RaidEvents.adminStopCmd(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> RaidEvents.adminRemoveCmd(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.literal("repair")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> RaidEvents.adminRepairCmd(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team")))))));
    }
}
