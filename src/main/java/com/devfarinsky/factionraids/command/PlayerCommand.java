package com.devfarinsky.factionraids.command;

import com.devfarinsky.factionraids.FactionLogger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Small helper that removes the repeated
 * {@code try { ServerPlayer player = source.getPlayerOrException(); ... } catch (Exception e) { ... }}
 * boilerplate that was duplicated 16 times inside {@code RaidEvents}.
 *
 * <p>Usage:
 * <pre>{@code
 *   return PlayerCommand.run(source, "Only a player can set a faction raid anchor.", player -> {
 *       // ...body that returns an int result...
 *   });
 * }</pre>
 */
public final class PlayerCommand {
    private PlayerCommand() {}

    /** Body of a player-only command. Return {@code 1} on success and {@code 0} on failure. */
    @FunctionalInterface
    public interface Body {
        int apply(ServerPlayer player) throws Exception;
    }

    /**
     * Run {@code body} with the command source's player. If no player is attached
     * (e.g. server console), send {@code notAPlayerMessage} and return {@code 0}.
     *
     * <p>Unexpected exceptions inside the body are caught, logged at debug level,
     * and surfaced as a generic failure so a scripting slip never crashes the server.
     */
    public static int run(CommandSourceStack source, String notAPlayerMessage, Body body) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal(notAPlayerMessage));
            FactionLogger.debugCommandFailure("resolve player", e);
            return 0;
        }
        try {
            return body.apply(player);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Command failed: " + e.getMessage()));
            FactionLogger.debugCommandFailure("command body", e);
            return 0;
        }
    }
}
