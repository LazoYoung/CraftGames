package io.github.lazoyoung.craftgames;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.event.EventManager;

public class Game {
    
    private Script script;
    
    public Game() {
        script = new Script(Sponge.getEventManager());
    }
    
    public void start() {
        // TODO Scripts must be able to access the non-private fields in this class
        // TODO Evaluate the script
        
    }
    
    /**
     * Register a event function in your game script.
     * @param eventClass The type of event. Use Java.type(String classpath)
     * @param listener The instance where you can implement your event function
     */
    void registerEvent(Class eventClass, EventListener listener) {
        script.registerListener(eventClass, listener);
    }
    
    /**
     * Register a scheduler function in your game script.
     */
    void registerScheduler() {
    
    }
    
}

class Script {
    
    private EventManager man;
    
    Script(EventManager man) throws IllegalArgumentException {
        this.man = man;
        
        if(man == null) {
            throw new IllegalArgumentException();
        }
    }
    
    void registerListener(Class eventClass, EventListener listener) {
        man.registerListener(CraftGames.getInstance(), eventClass, listener);
    }
    
}