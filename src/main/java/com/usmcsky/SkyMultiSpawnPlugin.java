package com.usmcsky;

import com.usmcsky.spawn.BedSpawnCommand;
import com.usmcsky.spawn.BedSpawnListener;
import com.usmcsky.spawn.BedSpawnManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkyMultiSpawnPlugin extends JavaPlugin {

    private BedSpawnManager bedSpawnManager;

    @Override
    public void onEnable() {
        this.bedSpawnManager = new BedSpawnManager(this);
        this.bedSpawnManager.load();

        BedSpawnCommand command = new BedSpawnCommand(bedSpawnManager);
        PluginCommand multiSpawnCommand = getCommand("multispawn");
        if (multiSpawnCommand == null) {
            throw new IllegalStateException("multispawn command is not defined in plugin.yml");
        }

        multiSpawnCommand.setExecutor(command);
        multiSpawnCommand.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new BedSpawnListener(bedSpawnManager), this);
    }

    @Override
    public void onDisable() {
        if (bedSpawnManager != null) {
            bedSpawnManager.save();
        }
    }
}
