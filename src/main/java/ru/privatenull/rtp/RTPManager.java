package ru.privatenull.rtp;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.privatenull.pnrtpPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public class RTPManager {

    private final pnrtpPlugin plugin;
    private final Map<UUID, Long> cooldowns;
    private final Random random;

    public RTPManager(pnrtpPlugin plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.random = new Random();
    }

    public void reloadConfig() {
        // Конфиг автоматически перезагружается через plugin.reloadConfig()
    }

    public boolean isOnCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        if (!cooldowns.containsKey(playerId)) {
            return false;
        }

        long cooldownEnd = cooldowns.get(playerId);
        long currentTime = System.currentTimeMillis();

        if (currentTime >= cooldownEnd) {
            cooldowns.remove(playerId);
            return false;
        }

        return true;
    }

    public long getRemainingCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        if (!cooldowns.containsKey(playerId)) {
            return 0;
        }

        long cooldownEnd = cooldowns.get(playerId);
        long currentTime = System.currentTimeMillis();
        long remaining = cooldownEnd - currentTime;

        return Math.max(0, remaining / 1000);
    }

    public void setCooldown(Player player, String mode) {
        UUID playerId = player.getUniqueId();
        int cooldownSeconds = plugin.getCooldown(mode);
        cooldowns.put(playerId, System.currentTimeMillis() + (cooldownSeconds * 1000L));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    public void teleportPlayer(Player player, String mode) {
        String targetWorldName = plugin.getConfig().getString("target-world", "world");
        World targetWorld = Bukkit.getWorld(targetWorldName);

        if (targetWorld == null) {
            plugin.sendMessage(player, "invalid-world");
            return;
        }

        // Проверяем worldborder
        if (plugin.isWorldBorderCheck()) {
            if (targetWorld.getWorldBorder() == null) {
                plugin.sendMessage(player, "invalid-world");
                return;
            }
        }

        // Логика поиска origin
        Location origin;
        if (mode.equalsIgnoreCase("near")) {
            int minPlayers = plugin.getConfig().getInt("modes.near.min-players", 2);
            if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                plugin.sendMessage(player, "not-enough-players");
                return;
            }

            java.util.List<Player> validTargets = new java.util.ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player) && p.getWorld().equals(targetWorld)) {
                    validTargets.add(p);
                }
            }

            if (validTargets.isEmpty()) {
                plugin.sendMessage(player, "not-enough-players");
                return;
            }

            Player randomTarget = validTargets.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(validTargets.size()));
            origin = randomTarget.getLocation();
        } else {
            // Для остальных режимов (default, far) всегда ищем от спавна мира, 
            // чтобы раскидывать игроков по всему миру, а не топтаться на месте
            origin = targetWorld.getSpawnLocation();
        }

        final Location finalOrigin = origin;

        plugin.sendMessage(player, "teleporting");

        // Запоминаем стартовую позицию для эффекта
        Location startLocation = player.getLocation().clone();

        // Работа с блоками и чанками должна выполняться на серверном потоке.
        // Проверяем по одной позиции за тик, чтобы не создавать длинную задержку.
        new BukkitRunnable() {
            private static final int MAX_ATTEMPTS = 1000;
            private int attempts;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                Location safeLocation = findSafeLocation(finalOrigin, mode, 1);
                attempts++;

                if (safeLocation == null) {
                    if (attempts >= MAX_ATTEMPTS) {
                        cancel();
                        plugin.sendMessage(player, "no-safe-location");
                    }
                    return;
                }

                cancel();
                completeTeleport(player, mode, startLocation, safeLocation);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void completeTeleport(Player player, String mode, Location startLocation, Location safeLocation) {
        playStartEffects(startLocation);

        safeLocation.getChunk().load();
        boolean success = player.teleport(safeLocation);
        if (!success) {
            plugin.sendMessage(player, "error");
            return;
        }

        setCooldown(player, mode);
        plugin.sendMessage(player, "teleport-success",
            "%x%", String.valueOf(safeLocation.getBlockX()),
            "%y%", String.valueOf(safeLocation.getBlockY()),
            "%z%", String.valueOf(safeLocation.getBlockZ()));
        plugin.sendTitle(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    playEndEffects(player.getLocation());
                }
            }
        }.runTaskLater(plugin, 3L);
    }

    private void playStartEffects(Location location) {
        if (!plugin.getConfig().getBoolean("effects.enabled", true)) return;

        World world = location.getWorld();
        if (world == null) return;

        try {
            String particleName = plugin.getConfig().getString("effects.start.particle", "PORTAL");
            int count = plugin.getConfig().getInt("effects.start.count", 50);
            Particle particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
            world.spawnParticle(particle, location.add(0, 1, 0), count, 0.5, 1.0, 0.5, 0.1);
        } catch (Exception ignored) {}

        try {
            String soundName = plugin.getConfig().getString("effects.start.sound", "ENTITY_ENDERMAN_TELEPORT");
            float volume = (float) plugin.getConfig().getDouble("effects.start.sound-volume", 1.0);
            float pitch = (float) plugin.getConfig().getDouble("effects.start.sound-pitch", 1.2);
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            world.playSound(location, sound, volume, pitch);
        } catch (Exception ignored) {}
    }

    private void playEndEffects(Location location) {
        if (!plugin.getConfig().getBoolean("effects.enabled", true)) return;

        World world = location.getWorld();
        if (world == null) return;

        try {
            String particleName = plugin.getConfig().getString("effects.end.particle", "END_ROD");
            int count = plugin.getConfig().getInt("effects.end.count", 80);
            Particle particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
            world.spawnParticle(particle, location.add(0, 1, 0), count, 0.5, 1.0, 0.5, 0.05);
        } catch (Exception ignored) {}

        try {
            String soundName = plugin.getConfig().getString("effects.end.sound", "ENTITY_PLAYER_LEVELUP");
            float volume = (float) plugin.getConfig().getDouble("effects.end.sound-volume", 0.8);
            float pitch = (float) plugin.getConfig().getDouble("effects.end.sound-pitch", 1.5);
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            world.playSound(location, sound, volume, pitch);
        } catch (Exception ignored) {}
    }

    private Location findSafeLocation(Location origin, String mode, int maxAttempts) {
        int radius = plugin.getRadius(mode);
        World world = origin.getWorld();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double offsetX = (random.nextDouble() * 2 - 1) * radius;
            double offsetZ = (random.nextDouble() * 2 - 1) * radius;

            double x = origin.getX() + offsetX;
            double z = origin.getZ() + offsetZ;

            Location location = findHighestSafeY(world, x, z);

            if (location != null && isLocationSafe(location)) {
                return location;
            }
        }

        return null;
    }

    private Location findHighestSafeY(World world, double x, double z) {
        int maxY = world.getMaxHeight();
        int minY = getMinWorldHeight(world);

        // Начинаем с максимальной высоты и спускаемся вниз
        for (int y = maxY - 1; y > minY; y--) {
            Block block = world.getBlockAt((int) Math.floor(x), y, (int) Math.floor(z));
            Material type = block.getType();

            // Ищем твердую поверхность
            if (isSolidBlock(type) && !isLeaves(block.getType())) {
                Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                return loc;
            }
        }

        return null;
    }

    private int getMinWorldHeight(World world) {
        try {
            Object value = world.getClass().getMethod("getMinHeight").invoke(world);
            return ((Number) value).intValue();
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return 0;
        }
    }

    private boolean isLocationSafe(Location location) {
        World world = location.getWorld();
        double checkRadius = plugin.getSafetyCheckRadius();

        // Проверяем центральный блок
        Block centerBlock = world.getBlockAt(location);
        Material centerType = centerBlock.getType();

        // Избегаем воды и лавы
        if (plugin.avoidWater() && isWater(centerType)) {
            return false;
        }
        if (plugin.avoidLava() && isLava(centerType)) {
            return false;
        }

        // Проверяем область вокруг (чтобы не оказаться в дереве)
        int checkBlocks = (int) Math.ceil(checkRadius);

        for (int x = -checkBlocks; x <= checkBlocks; x++) {
            for (int y = 0; y <= checkBlocks + 2; y++) {
                for (int z = -checkBlocks; z <= checkBlocks; z++) {
                    Block block = world.getBlockAt(
                            (int) (location.getX() + x),
                            (int) (location.getY() + y),
                            (int) (location.getZ() + z)
                    );

                    Material type = block.getType();

                    // Проверяем опасные блоки
                    if (plugin.avoidWater() && isWater(type)) {
                        return false;
                    }
                    if (plugin.avoidLava() && isLava(type)) {
                        return false;
                    }

                    // Избегаем деревьев
                    if (plugin.avoidTrees() && (isLeaves(type) || isLog(type))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean isSolidBlock(Material type) {
        return type.isSolid() && !type.isAir();
    }

    private boolean isWater(Material type) {
        return type == Material.WATER || type == Material.LAVA;
    }

    private boolean isLava(Material type) {
        return type == Material.LAVA;
    }

    private boolean isLeaves(Material type) {
        return type.name().endsWith("LEAVES") || type.name().endsWith("LEAF");
    }

    private boolean isLog(Material type) {
        return type.name().endsWith("LOG") || type.name().endsWith("WOOD");
    }

}
