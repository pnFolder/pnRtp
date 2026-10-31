package ru.privatenull.bootstrap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import ru.privatenull.pnrtpPlugin;

/** Не даёт pnRtp пережить общий рантайм при остановке сервера или ручной выгрузке. */
public final class PnLibraryLifecycleListener implements Listener {

    private final pnrtpPlugin plugin;

    public PnLibraryLifecycleListener(pnrtpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equalsIgnoreCase("pnLibrary") || !plugin.isEnabled()) return;
        plugin.getLogger().warning("pnLibrary отключается; pnRtp будет безопасно остановлен первым.");
        plugin.getServer().getPluginManager().disablePlugin(plugin);
    }
}
