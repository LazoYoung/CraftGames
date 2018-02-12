package io.github.lazoyoung.craftgames;

import org.spongepowered.api.event.Event;

public abstract class ScriptEventListener {
    
    private Event event;
    
    public ScriptEventListener(Event event) {
        this.event = event;
    }
    
    public abstract void handle();
    
    public Event getEvent() {
        return event;
    }
    
}