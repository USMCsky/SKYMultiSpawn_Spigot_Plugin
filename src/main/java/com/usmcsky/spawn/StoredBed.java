package com.usmcsky.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;

public record StoredBed(String worldName, int x, int y, int z) {

    public static StoredBed fromBlock(Block block) {
        Block normalized = normalizeBedBlock(block);
        Location location = normalized.getLocation();
        return new StoredBed(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Block getBlock() {
        World world = getWorld();
        if (world == null) {
            return null;
        }

        return world.getBlockAt(x, y, z);
    }

    public boolean isValid() {
        Block block = getBlock();
        return block != null && isBed(block.getType());
    }

    public Location getDisplayLocation() {
        World world = getWorld();
        if (world == null) {
            return null;
        }

        return new Location(world, x, y, z);
    }

    public static Block normalizeBedBlock(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Bed bedData) || bedData.getPart() != Bed.Part.HEAD) {
            return block;
        }

        return block.getRelative(bedData.getFacing().getOppositeFace());
    }

    public static boolean isBed(Material material) {
        return material.name().endsWith("_BED");
    }
}
