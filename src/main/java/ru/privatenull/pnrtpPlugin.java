package ru.privatenull;

import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.rtp.RTPCommandExecutor;
import ru.privatenull.rtp.RTPListener;
import ru.privatenull.rtp.RTPManager;
import ru.privatenull.rtp.RTPTabCompleter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class pnrtpPlugin extends JavaPlugin {

    public static final String SUPPORT_DISCORD = "https://discord.gg/rRbzq6cnc6";

    // Паттерн для &#RRGGBB формата (hex shortcut)
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    // Паттерн для &x&R&R&G&G&B&B формата (legacy BungeeCord hex)
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("&x(&[A-Fa-f0-9]){6}");

    private RTPManager rtpManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        rtpManager = new RTPManager(this);

        getCommand("rtp").setExecutor(new RTPCommandExecutor(this, rtpManager));
        getCommand("rtp").setTabCompleter(new RTPTabCompleter());

        getServer().getPluginManager().registerEvents(new RTPListener(rtpManager, this), this);

        logStartupMessage();
    }

    @Override
    public void onDisable() {
        getLogger().info("pnRtp отключён");
    }

    public void reloadPlugin() {
        reloadConfig();
        rtpManager.reloadConfig();
        getLogger().info("pnRtp: конфигурация перезагружена.");
    }

    public void sendMessage(org.bukkit.command.CommandSender sender, String path, String... replacements) {
        String prefix = getConfig().getString("prefix", "&8[&6pnRtp&8] ");
        
        if (getConfig().isList("messages." + path)) {
            for (String line : getConfig().getStringList("messages." + path)) {
                sender.sendMessage(colorize(prefix + applyReplacements(line, replacements)));
            }
        } else {
            String msg = getConfig().getString("messages." + path);
            if (msg != null && !msg.isEmpty()) {
                sender.sendMessage(colorize(prefix + applyReplacements(msg, replacements)));
            }
        }
    }

    private String applyReplacements(String text, String... replacements) {
        if (text == null) return "";
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                text = text.replace(replacements[i], replacements[i + 1]);
            }
        }
        return text;
    }

    /**
     * Конвертирует текст с поддержкой:
     * 1. &#RRGGBB - hex shortcut (например &#FF5555)
     * 2. &x&R&R&G&G&B&B - legacy BungeeCord hex
     * 3. &c, &a, &l и т.д. - стандартные цветовые коды
     * 4. <#RRGGBB> - MiniMessage-like hex
     * 
     * Возвращает legacy-строку, совместимую с Paper 1.16.5 и новее.
     */
    public String colorize(String message) {
        if (message == null) return "";
        
        // 1) Конвертируем &#RRGGBB -> §x§R§R§G§G§B§B
        Matcher hexMatcher = HEX_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            hexMatcher.appendReplacement(sb, replacement.toString());
        }
        hexMatcher.appendTail(sb);
        message = sb.toString();
        
        // 2) Конвертируем &x&R&R&G&G&B&B -> §x§R§R§G§G§B§B
        Matcher legacyHexMatcher = LEGACY_HEX_PATTERN.matcher(message);
        sb = new StringBuffer();
        while (legacyHexMatcher.find()) {
            String match = legacyHexMatcher.group();
            legacyHexMatcher.appendReplacement(sb, match.replace("&", "§"));
        }
        legacyHexMatcher.appendTail(sb);
        message = sb.toString();
        
        // 3) Конвертируем <#RRGGBB> -> §x§R§R§G§G§B§B
        Pattern miniHexPattern = Pattern.compile("<#([A-Fa-f0-9]{6})>");
        Matcher miniHexMatcher = miniHexPattern.matcher(message);
        sb = new StringBuffer();
        while (miniHexMatcher.find()) {
            String hex = miniHexMatcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            miniHexMatcher.appendReplacement(sb, replacement.toString());
        }
        miniHexMatcher.appendTail(sb);
        message = sb.toString();
        
        // 4) Конвертируем обычные &коды -> §коды
        message = message.replace("&", "§");
        
        return message;
    }

    /**
     * Colorize для строки (используется в titles)
     */
    public String colorizeToString(String message) {
        if (message == null) return "";
        
        // Конвертируем hex форматы 
        Matcher hexMatcher = HEX_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            hexMatcher.appendReplacement(sb, replacement.toString());
        }
        hexMatcher.appendTail(sb);
        message = sb.toString();
        
        Matcher legacyHexMatcher = LEGACY_HEX_PATTERN.matcher(message);
        sb = new StringBuffer();
        while (legacyHexMatcher.find()) {
            String match = legacyHexMatcher.group();
            legacyHexMatcher.appendReplacement(sb, match.replace("&", "§"));
        }
        legacyHexMatcher.appendTail(sb);
        message = sb.toString();
        
        Pattern miniHexPattern = Pattern.compile("<#([A-Fa-f0-9]{6})>");
        Matcher miniHexMatcher = miniHexPattern.matcher(message);
        sb = new StringBuffer();
        while (miniHexMatcher.find()) {
            String hex = miniHexMatcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            miniHexMatcher.appendReplacement(sb, replacement.toString());
        }
        miniHexMatcher.appendTail(sb);
        message = sb.toString();
        
        message = message.replace("&", "§");
        return message;
    }

    public void sendTitle(org.bukkit.entity.Player player) {
        if (!getConfig().getBoolean("titles.enabled", true)) return;
        
        String up = colorizeToString(getConfig().getString("titles.up", ""));
        String down = colorizeToString(getConfig().getString("titles.down", ""));
        int fadeIn = getConfig().getInt("titles.fadeIn", 10);
        int stay = getConfig().getInt("titles.stay", 40);
        int fadeOut = getConfig().getInt("titles.fadeOut", 10);
        
        player.sendTitle(up, down, fadeIn, stay, fadeOut);
    }

    public int getRadius(String mode) {
        return getConfig().getInt("modes." + mode + ".radius", 1000);
    }

    public int getCooldown(String mode) {
        return getConfig().getInt("modes." + mode + ".cooldown", 30);
    }

    public boolean isWorldBorderCheck() {
        return getConfig().getBoolean("worldborder-check", true);
    }

    public boolean avoidWater() {
        return getConfig().getBoolean("avoid-water", true);
    }

    public boolean avoidLava() {
        return getConfig().getBoolean("avoid-lava", true);
    }

    public boolean avoidTrees() {
        return getConfig().getBoolean("avoid-trees", true);
    }

    public double getSafetyCheckRadius() {
        return getConfig().getDouble("safety-check-radius", 5.0);
    }

    public RTPManager getRTPManager() {
        return rtpManager;
    }

    private void logStartupMessage() {
        logBanner();
        getLogger().info("pnRtp v" + getDescription().getVersion() + " включён");
        getLogger().info("Поддержка pnRtp: " + SUPPORT_DISCORD);
    }

    private void logBanner() {
        getLogger().info("██████╗░██████╗░██████╗░██████╗░██████╗░████████╗███████╗██████╗░");
        getLogger().info("██╔══██╗██╔══██╗██╔══██╗██╔══██╗██╔══██╗╚══██╔══╝██╔════╝██╔══██╗");
        getLogger().info("██████╔╝██████╔╝██████╔╝██████╔╝██████╔╝░░░██║░░░█████╗░░██████╔╝");
        getLogger().info("██╔═══╝░██╔══██╗██╔══██╗██╔══██╗██╔══██╗░░░██║░░░██╔══╝░░██╔══██╗");
        getLogger().info("██║░░░░░██║░░██║██║░░██║██║░░██║██║░░██║░░░██║░░░███████╗██║░░██║");
        getLogger().info("╚═╝░░░░░╚═╝░░╚═╝╚═╝░░╚═╝╚═╝░░╚═╝╚═╝░░╚═╝░░░╚═╝░░░╚══════╝╚═╝░░╚═╝");
    }
}
