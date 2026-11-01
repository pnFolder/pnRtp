package ru.privatenull.rtp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.pnfolder.pnlibrary.api.commands.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.BadUsage;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.NoPermission;
import org.pnfolder.pnlibrary.api.commands.PlayerOnly;
import ru.privatenull.pnrtpPlugin;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Команда /rtp на библиотечных командах pnLibrary: права, подсказки и ответы на ошибки описаны деревом. */
public final class RTPCommand {

    private final pnrtpPlugin plugin;
    private final RTPManager rtpManager;

    public RTPCommand(pnrtpPlugin plugin, RTPManager rtpManager) {
        this.plugin = plugin;
        this.rtpManager = rtpManager;
    }

    public CommandDefinition definition() {
        return CommandDefinition.builder("rtp")
                .playerOnly()
                .onError(PlayerOnly.class, (context, error) ->
                        context.getSender().send(Component.text("Эта команда только для игроков!")))
                .onError(NoPermission.class, (context, error) -> message(context, "no-permission"))
                .onError(BadUsage.class, (context, error) -> message(context, "usage"))
                .executes(context -> message(context, "usage"))
                .literal("reload", node -> node
                        .permission("pnrtp.admin")
                        .executes(context -> {
                            Player player = player(context);
                            if (player == null) return;
                            plugin.reloadPlugin();
                            player.sendMessage(plugin.colorize("&#55FF55Конфигурация перезагружена!"));
                        }))
                .literal("resetcd", node -> node
                        .permission("pnrtp.admin")
                        .argument("player", ArgumentType.Companion.string(), argument -> argument
                                .suggests(context -> {
                                    String prefix = context.getCurrentInput().toLowerCase();
                                    return Bukkit.getOnlinePlayers().stream()
                                            .map(Player::getName)
                                            .filter(name -> name.toLowerCase().startsWith(prefix))
                                            .collect(Collectors.<String>toList());
                                })
                                .executes(context -> {
                                    Player player = player(context);
                                    if (player == null) return;
                                    String name = context.<String>get("player");
                                    Player target = Bukkit.getPlayer(name);
                                    if (target == null) {
                                        plugin.sendMessage(player, "player-not-found", "%player%", name);
                                        return;
                                    }
                                    rtpManager.resetCooldown(target);
                                    plugin.sendMessage(player, "reset-success", "%player%", target.getName());
                                    plugin.sendMessage(target, "cooldown-reset-target");
                                })))
                .literal("near", node -> mode(node, "near"))
                .literal("default", node -> mode(node, "default"))
                .literal("far", node -> mode(node, "far"))
                .build();
    }

    private void mode(org.pnfolder.pnlibrary.api.commands.CommandNodeBuilder node, String mode) {
        node.permission("pnrtp." + mode).executes(context -> {
            Player player = player(context);
            if (player == null) return;
            if (!player.hasPermission("pnrtp.bypass") && rtpManager.isOnCooldown(player)) {
                long remaining = rtpManager.getRemainingCooldown(player);
                plugin.sendMessage(player, "cooldown", "%time%", String.valueOf(remaining));
                return;
            }
            rtpManager.teleportPlayer(player, mode);
        });
    }

    private void message(CommandContext context, String path) {
        Player player = player(context);
        if (player != null) plugin.sendMessage(player, path);
    }

    private static Player player(CommandContext context) {
        return Bukkit.getPlayer(UUID.fromString(context.getSender().getId()));
    }
}
