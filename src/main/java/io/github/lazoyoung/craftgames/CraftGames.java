package io.github.lazoyoung.craftgames;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
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
     * @return the plugin instance. This will return null until plugin gets initialized.
     */
    public static CraftGames getInstance() {
        return holder.obj;
    }
    
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
