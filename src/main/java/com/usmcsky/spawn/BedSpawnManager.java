package com.usmcsky.spawn;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BedSpawnManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<UUID, PlayerBedData> playerData = new HashMap<>();
    private FileConfiguration configuration;

    public BedSpawnManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "bed-spawns.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }

        this.configuration = YamlConfiguration.loadConfiguration(dataFile);
        this.playerData.clear();

        ConfigurationSection playersSection = configuration.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String uuidKey : playersSection.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid player UUID in bed-spawns.yml: " + uuidKey);
                continue;
            }

            ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidKey);
            if (playerSection == null) {
                continue;
            }

            PlayerBedData data = new PlayerBedData();
            data.setActiveSlot(normalizeSlot(playerSection.getInt("active-slot", 0)));

            ConfigurationSection slotsSection = playerSection.getConfigurationSection("slots");
            if (slotsSection != null) {
                for (String slotKey : slotsSection.getKeys(false)) {
                    int slot;
                    try {
                        slot = Integer.parseInt(slotKey);
                    } catch (NumberFormatException exception) {
                        continue;
                    }

                    if (!isValidSlot(slot)) {
                        continue;
                    }

                    ConfigurationSection slotSection = slotsSection.getConfigurationSection(slotKey);
                    if (slotSection == null) {
                        continue;
                    }

                    String world = slotSection.getString("world");
                    if (world == null || world.isBlank()) {
                        continue;
                    }

                    data.setBed(slot, new StoredBed(
                        world,
                        slotSection.getInt("x"),
                        slotSection.getInt("y"),
                        slotSection.getInt("z")
                    ));
                }
            }

            if (!data.isEmpty()) {
                if (data.getActiveSlot() == 0 || data.getBed(data.getActiveSlot()) == null) {
                    data.setActiveSlot(data.findFirstStoredSlot());
                }
                playerData.put(playerId, data);
            }
        }
    }

    public void save() {
        configuration.set("players", null);
        for (Map.Entry<UUID, PlayerBedData> entry : playerData.entrySet()) {
            writePlayer(entry.getKey(), entry.getValue());
        }

        try {
            configuration.save(dataFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save bed-spawns.yml", exception);
        }
    }

    public Map<Integer, StoredBed> getBeds(UUID playerId) {
        PlayerBedData data = playerData.get(playerId);
        if (data == null) {
            return Collections.emptyMap();
        }

        return data.getBeds();
    }

    public StoredBed getBed(UUID playerId, int slot) {
        PlayerBedData data = playerData.get(playerId);
        if (data == null) {
            return null;
        }

        return data.getBed(slot);
    }

    public int getActiveSlot(UUID playerId) {
        PlayerBedData data = playerData.get(playerId);
        return data == null ? 0 : data.getActiveSlot();
    }

    public void setBed(UUID playerId, int slot, StoredBed storedBed) {
        validateSlot(slot);
        PlayerBedData data = playerData.computeIfAbsent(playerId, ignored -> new PlayerBedData());
        data.setBed(slot, storedBed);
        if (data.getActiveSlot() == 0) {
            data.setActiveSlot(slot);
        }
        writePlayer(playerId, data);
        save();
    }

    public boolean setActiveSlot(UUID playerId, int slot) {
        validateSlot(slot);
        PlayerBedData data = playerData.get(playerId);
        if (data == null || data.getBed(slot) == null) {
            return false;
        }

        data.setActiveSlot(slot);
        writePlayer(playerId, data);
        save();
        return true;
    }

    public boolean removeBed(UUID playerId, int slot) {
        validateSlot(slot);
        PlayerBedData data = playerData.get(playerId);
        if (data == null || data.getBed(slot) == null) {
            return false;
        }

        data.removeBed(slot);
        if (data.getActiveSlot() == slot) {
            data.setActiveSlot(data.findFirstStoredSlot());
        }

        if (data.isEmpty()) {
            playerData.remove(playerId);
            configuration.set("players." + playerId, null);
        } else {
            writePlayer(playerId, data);
        }

        save();
        return true;
    }

    public StoredBed resolveRespawnBed(UUID playerId) {
        PlayerBedData data = playerData.get(playerId);
        if (data == null) {
            return null;
        }

        StoredBed activeBed = data.getBed(data.getActiveSlot());
        if (activeBed != null && activeBed.isValid()) {
            return activeBed;
        }

        for (int slot = 1; slot <= 3; slot++) {
            StoredBed candidate = data.getBed(slot);
            if (candidate != null && candidate.isValid()) {
                if (data.getActiveSlot() != slot) {
                    data.setActiveSlot(slot);
                    writePlayer(playerId, data);
                    save();
                }
                return candidate;
            }
        }

        return null;
    }

    public String describeBed(StoredBed storedBed) {
        Location location = storedBed.getDisplayLocation();
        if (location == null) {
            return "missing world";
        }

        World world = location.getWorld();
        return world.getName() + " (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }

    private void writePlayer(UUID playerId, PlayerBedData data) {
        String basePath = "players." + playerId;
        configuration.set(basePath + ".active-slot", data.getActiveSlot());
        configuration.set(basePath + ".slots", null);

        for (Map.Entry<Integer, StoredBed> entry : data.getBeds().entrySet()) {
            StoredBed storedBed = entry.getValue();
            String slotPath = basePath + ".slots." + entry.getKey();
            configuration.set(slotPath + ".world", storedBed.worldName());
            configuration.set(slotPath + ".x", storedBed.x());
            configuration.set(slotPath + ".y", storedBed.y());
            configuration.set(slotPath + ".z", storedBed.z());
        }
    }

    private static void validateSlot(int slot) {
        if (!isValidSlot(slot)) {
            throw new IllegalArgumentException("Slot must be between 1 and 3");
        }
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 1 && slot <= 3;
    }

    private static int normalizeSlot(int slot) {
        return isValidSlot(slot) ? slot : 0;
    }

    private static final class PlayerBedData {
        private final Map<Integer, StoredBed> beds = new HashMap<>();
        private int activeSlot;

        public Map<Integer, StoredBed> getBeds() {
            return new HashMap<>(beds);
        }

        public StoredBed getBed(int slot) {
            return beds.get(slot);
        }

        public void setBed(int slot, StoredBed storedBed) {
            beds.put(slot, storedBed);
        }

        public void removeBed(int slot) {
            beds.remove(slot);
        }

        public int getActiveSlot() {
            return activeSlot;
        }

        public void setActiveSlot(int activeSlot) {
            this.activeSlot = activeSlot;
        }

        public int findFirstStoredSlot() {
            for (int slot = 1; slot <= 3; slot++) {
                if (beds.containsKey(slot)) {
                    return slot;
                }
            }
            return 0;
        }

        public boolean isEmpty() {
            return beds.isEmpty();
        }
    }
}
