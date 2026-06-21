package com.usmcsky.spawn;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BedSpawnCommand implements TabExecutor {

    public static final String GUI_TITLE = ChatColor.DARK_AQUA + "Select Spawn Bed";
    private static final List<String> SUBCOMMANDS = List.of("set", "select", "remove", "list", "gui");

    private final BedSpawnManager bedSpawnManager;

    public BedSpawnCommand(BedSpawnManager bedSpawnManager) {
        this.bedSpawnManager = bedSpawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "set" -> handleSet(player, args);
            case "select" -> handleSelect(player, args);
            case "remove" -> handleRemove(player, args);
            case "list" -> handleList(player);
            case "gui" -> handleGui(player);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (args.length == 2 && Arrays.asList("set", "select", "remove").contains(args[0].toLowerCase(Locale.ROOT))) {
            return completeSlots(args[1]);
        }

        return Collections.emptyList();
    }

    public void openGui(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 9, GUI_TITLE);
        UUID playerId = player.getUniqueId();
        int activeSlot = bedSpawnManager.getActiveSlot(playerId);
        Map<Integer, StoredBed> beds = bedSpawnManager.getBeds(playerId);

        inventory.setItem(2, createSlotItem(1, beds.get(1), activeSlot == 1));
        inventory.setItem(4, createSlotItem(2, beds.get(2), activeSlot == 2));
        inventory.setItem(6, createSlotItem(3, beds.get(3), activeSlot == 3));
        player.openInventory(inventory);
    }

    public static int inventorySlotToBedSlot(int inventorySlot) {
        return switch (inventorySlot) {
            case 2 -> 1;
            case 4 -> 2;
            case 6 -> 3;
            default -> 0;
        };
    }

    private boolean handleSet(Player player, String[] args) {
        Integer slot = parseSlot(player, args, 1);
        if (slot == null) {
            return true;
        }

        Block bedBlock = findBedTarget(player);
        if (bedBlock == null) {
            player.sendMessage(ChatColor.RED + "Look at a bed within 5 blocks, or stand on one, before using /multispawn set " + slot + ".");
            return true;
        }

        StoredBed storedBed = StoredBed.fromBlock(bedBlock);
        bedSpawnManager.setBed(player.getUniqueId(), slot, storedBed);
        player.sendMessage(ChatColor.GREEN + "Saved slot " + slot + " to " + bedSpawnManager.describeBed(storedBed) + ".");
        return true;
    }

    private boolean handleSelect(Player player, String[] args) {
        Integer slot = parseSlot(player, args, 1);
        if (slot == null) {
            return true;
        }

        StoredBed storedBed = bedSpawnManager.getBed(player.getUniqueId(), slot);
        if (storedBed == null) {
            player.sendMessage(ChatColor.RED + "Slot " + slot + " has no saved bed.");
            return true;
        }

        if (!storedBed.isValid()) {
            player.sendMessage(ChatColor.RED + "Slot " + slot + " points to a missing or broken bed.");
            return true;
        }

        bedSpawnManager.setActiveSlot(player.getUniqueId(), slot);
        player.sendMessage(ChatColor.GREEN + "Selected slot " + slot + " as your active spawn.");
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        Integer slot = parseSlot(player, args, 1);
        if (slot == null) {
            return true;
        }

        if (!bedSpawnManager.removeBed(player.getUniqueId(), slot)) {
            player.sendMessage(ChatColor.RED + "Slot " + slot + " has no saved bed.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Removed saved bed from slot " + slot + ".");
        return true;
    }

    private boolean handleList(Player player) {
        UUID playerId = player.getUniqueId();
        int activeSlot = bedSpawnManager.getActiveSlot(playerId);
        Map<Integer, StoredBed> beds = bedSpawnManager.getBeds(playerId);

        player.sendMessage(ChatColor.GOLD + "Saved spawn beds:");
        for (int slot = 1; slot <= 3; slot++) {
            StoredBed storedBed = beds.get(slot);
            if (storedBed == null) {
                player.sendMessage(ChatColor.GRAY + "- Slot " + slot + ": empty");
                continue;
            }

            String marker = activeSlot == slot ? ChatColor.GREEN + " [active]" : "";
            String state = storedBed.isValid() ? ChatColor.WHITE + bedSpawnManager.describeBed(storedBed) : ChatColor.RED + "missing or broken";
            player.sendMessage(ChatColor.GRAY + "- Slot " + slot + ": " + state + marker);
        }

        return true;
    }

    private boolean handleGui(Player player) {
        openGui(player);
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "Usage:");
        player.sendMessage(ChatColor.GRAY + "/multispawn set <1|2|3> " + ChatColor.WHITE + "- save the bed you are looking at");
        player.sendMessage(ChatColor.GRAY + "/multispawn select <1|2|3> " + ChatColor.WHITE + "- choose your active spawn bed");
        player.sendMessage(ChatColor.GRAY + "/multispawn remove <1|2|3> " + ChatColor.WHITE + "- clear a saved bed");
        player.sendMessage(ChatColor.GRAY + "/multispawn list " + ChatColor.WHITE + "- show your saved beds");
        player.sendMessage(ChatColor.GRAY + "/multispawn gui " + ChatColor.WHITE + "- open the bed selector");
    }

    private Integer parseSlot(Player player, String[] args, int index) {
        if (args.length <= index) {
            player.sendMessage(ChatColor.RED + "Pick a slot between 1 and 3.");
            return null;
        }

        int slot;
        try {
            slot = Integer.parseInt(args[index]);
        } catch (NumberFormatException exception) {
            player.sendMessage(ChatColor.RED + "Slot must be 1, 2, or 3.");
            return null;
        }

        if (slot < 1 || slot > 3) {
            player.sendMessage(ChatColor.RED + "Slot must be 1, 2, or 3.");
            return null;
        }

        return slot;
    }

    private List<String> completeSlots(String prefix) {
        List<String> matches = new ArrayList<>();
        for (String slot : List.of("1", "2", "3")) {
            if (slot.startsWith(prefix)) {
                matches.add(slot);
            }
        }
        return matches;
    }

    private Block findBedTarget(Player player) {
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock != null && StoredBed.isBed(targetBlock.getType())) {
            return targetBlock;
        }

        Block standingBlock = player.getLocation().getBlock();
        if (StoredBed.isBed(standingBlock.getType())) {
            return standingBlock;
        }

        Block below = standingBlock.getRelative(0, -1, 0);
        if (StoredBed.isBed(below.getType())) {
            return below;
        }

        return null;
    }

    private ItemStack createSlotItem(int slot, StoredBed storedBed, boolean active) {
        Material material = switch (slot) {
            case 1 -> Material.RED_BED;
            case 2 -> Material.YELLOW_BED;
            default -> Material.LIME_BED;
        };

        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }

        meta.setDisplayName((active ? ChatColor.GREEN : ChatColor.AQUA) + "Spawn Slot " + slot);

        List<String> lore = new ArrayList<>();
        if (storedBed == null) {
            lore.add(ChatColor.GRAY + "No bed saved");
            lore.add(ChatColor.DARK_GRAY + "Use /multispawn set " + slot);
        } else if (storedBed.isValid()) {
            lore.add(ChatColor.WHITE + bedSpawnManager.describeBed(storedBed));
            lore.add(active ? ChatColor.GREEN + "Currently selected" : ChatColor.YELLOW + "Click to select");
        } else {
            lore.add(ChatColor.RED + "Saved bed is missing or broken");
            lore.add(ChatColor.DARK_GRAY + "Reset this slot with /multispawn set " + slot);
        }

        meta.setLore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }
}
