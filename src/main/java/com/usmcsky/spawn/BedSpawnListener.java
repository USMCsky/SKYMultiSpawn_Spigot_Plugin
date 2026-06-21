package com.usmcsky.spawn;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class BedSpawnListener implements Listener {

    private final BedSpawnManager bedSpawnManager;

    public BedSpawnListener(BedSpawnManager bedSpawnManager) {
        this.bedSpawnManager = bedSpawnManager;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        StoredBed storedBed = bedSpawnManager.resolveRespawnBed(event.getPlayer().getUniqueId());
        if (storedBed == null) {
            return;
        }

        Block bedBlock = storedBed.getBlock();
        if (bedBlock == null) {
            return;
        }

        event.setRespawnLocation(findRespawnLocation(bedBlock));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!BedSpawnCommand.GUI_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int bedSlot = BedSpawnCommand.inventorySlotToBedSlot(event.getRawSlot());
        if (bedSlot == 0) {
            return;
        }

        StoredBed storedBed = bedSpawnManager.getBed(player.getUniqueId(), bedSlot);
        if (storedBed == null) {
            player.sendMessage(ChatColor.RED + "Slot " + bedSlot + " has no saved bed.");
            return;
        }

        if (!storedBed.isValid()) {
            player.sendMessage(ChatColor.RED + "Slot " + bedSlot + " points to a missing or broken bed.");
            return;
        }

        bedSpawnManager.setActiveSlot(player.getUniqueId(), bedSlot);
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Selected slot " + bedSlot + " as your active spawn.");
    }

    private Location findRespawnLocation(Block bedBlock) {
        Block footBlock = StoredBed.normalizeBedBlock(bedBlock);
        Block headBlock = getHeadBlock(footBlock);

        Block[] candidates = new Block[] {
            footBlock.getRelative(BlockFace.UP),
            headBlock.getRelative(BlockFace.UP),
            footBlock.getRelative(BlockFace.UP).getRelative(BlockFace.NORTH),
            footBlock.getRelative(BlockFace.UP).getRelative(BlockFace.SOUTH),
            footBlock.getRelative(BlockFace.UP).getRelative(BlockFace.EAST),
            footBlock.getRelative(BlockFace.UP).getRelative(BlockFace.WEST),
            headBlock.getRelative(BlockFace.UP).getRelative(BlockFace.NORTH),
            headBlock.getRelative(BlockFace.UP).getRelative(BlockFace.SOUTH),
            headBlock.getRelative(BlockFace.UP).getRelative(BlockFace.EAST),
            headBlock.getRelative(BlockFace.UP).getRelative(BlockFace.WEST)
        };

        for (Block candidate : candidates) {
            if (isSafeSpawnBlock(candidate)) {
                return candidate.getLocation().add(0.5D, 0.0D, 0.5D);
            }
        }

        return footBlock.getRelative(BlockFace.UP).getLocation().add(0.5D, 0.0D, 0.5D);
    }

    private Block getHeadBlock(Block footBlock) {
        BlockData data = footBlock.getBlockData();
        if (data instanceof Bed bedData) {
            return footBlock.getRelative(bedData.getFacing());
        }

        return footBlock;
    }

    private boolean isSafeSpawnBlock(Block block) {
        Block headSpace = block.getRelative(BlockFace.UP);
        Block below = block.getRelative(BlockFace.DOWN);
        return block.isPassable()
            && headSpace.isPassable()
            && (below.getType().isSolid() || StoredBed.isBed(below.getType()));
    }
}
