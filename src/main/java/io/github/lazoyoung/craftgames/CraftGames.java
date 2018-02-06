package io.github.lazoyoung.craftgames;

import com.google.inject.Inject;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;

import java.nio.file.Path;

public class CraftGames {
    
    @Inject
    private Path configDir;
    
    private static ObjectHolder holder;
    
    
    public static CraftGames getInstance() {
        return holder.obj;
    }
    
    @Listener
    public void initPlugin(GamePreInitializationEvent event) {
        holder = new ObjectHolder(this);
    }
    
}

class ObjectHolder {
    CraftGames obj;
    
    ObjectHolder(CraftGames obj) {
        this.obj = obj;
    }
}
