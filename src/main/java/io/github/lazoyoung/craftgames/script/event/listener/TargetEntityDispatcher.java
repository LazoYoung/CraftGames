package io.github.lazoyoung.craftgames.script.event.listener;

import org.spongepowered.api.event.entity.TargetEntityEvent;

public class TargetEntityDispatcher extends ScriptEventDispatcher<TargetEntityEvent> {
    
    public TargetEntityDispatcher(String eventName) {
        super(eventName);
    }
    
    @Override
    public void handle(TargetEntityEvent event) throws Exception {
        callScriptListeners(event);
    }
    
}