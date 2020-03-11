package com.github.lazoyoung.LegacyCore;

import com.github.lazoyoung.LegacyCore.script.command.ScriptCommand;
import com.github.lazoyoung.LegacyCore.script.event.listener.TargetBlockDispatcher;
import com.github.lazoyoung.LegacyCore.script.event.listener.TargetEntityDispatcher;
import com.github.lazoyoung.LegacyCore.script.event.listener.TargetTileEntityDispatcher;
import com.github.lazoyoung.LegacyCore.script.event.listener.TargetUserDispatcher;
import com.google.inject.Inject;
import com.github.lazoyoung.LegacyCore.script.ScriptRegistry;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.TargetBlockEvent;
import org.spongepowered.api.event.block.tileentity.TargetTileEntityEvent;
import org.spongepowered.api.event.entity.TargetEntityEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.user.TargetUserEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;

import java.nio.file.Path;

@Plugin(id = "craftgames", name = "CraftGames", version = "0.1.0", authors = "LazoYoung")
public class CraftGames {
    
    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;
    
    @Inject
    private Logger log;
    
    private static ObjectHolder holder;
    
    
    /**
     * @return the plugin instance. This is null until this plugin gets initialized.
     */
    public static CraftGames getInstance() {
        return holder.obj;
    }
    
    /**
     * @return the logger instance. This is null until this plugin gets initialized.
     */
    public Logger getLogger() {
        return log;
    }
    
    public Path getConfigDir() {
        return configDir;
    }
    
    @Listener
    public void initPlugin(GamePreInitializationEvent event) {
        holder = new ObjectHolder(this);
        Sponge.getCommandManager().register(getInstance(), buildCommand(), "cg", "craftgames");
        initScriptEventDispatchers();
    }
    
    private void initScriptEventDispatchers() {
        EventManager man = Sponge.getEventManager();
        TargetBlockDispatcher targetBlock = new TargetBlockDispatcher("TargetBlockEvent");
        TargetEntityDispatcher targetEntity = new TargetEntityDispatcher("TargetEntityEvent");
        TargetTileEntityDispatcher targetTileEntity = new TargetTileEntityDispatcher("TargetTileEntityEvent");
        TargetUserDispatcher targetUser = new TargetUserDispatcher("TargetUserEvent");
        
        ScriptRegistry.addEventDispatcher(targetBlock);
        ScriptRegistry.addEventDispatcher(targetEntity);
        ScriptRegistry.addEventDispatcher(targetTileEntity);
        ScriptRegistry.addEventDispatcher(targetUser);
        
        man.registerListener(getInstance(), TargetBlockEvent.class, targetBlock);
        man.registerListener(getInstance(), TargetEntityEvent.class, targetEntity);
        man.registerListener(getInstance(), TargetTileEntityEvent.class, targetTileEntity);
        man.registerListener(getInstance(), TargetUserEvent.class, targetUser);
    }
    
    private CommandSpec buildCommand() {
        // TODO Implement reloading scripts.
        
        return CommandSpec.builder()
                .description(Text.of("CraftGames Command"))
                .child(new ScriptCommand().get(), "script")
                .build();
    }
}

class ObjectHolder {
    CraftGames obj;
    
    ObjectHolder(CraftGames obj) {
        this.obj = obj;
    }
}
