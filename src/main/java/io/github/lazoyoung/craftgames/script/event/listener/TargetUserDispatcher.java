package io.github.lazoyoung.craftgames.script.event.listener;

import org.spongepowered.api.event.user.TargetUserEvent;

public class TargetUserDispatcher extends ScriptEventDispatcher<TargetUserEvent> {
    
    public TargetUserDispatcher(String eventName) {
        super(eventName);
    }
    
    @Override
    public void handle(TargetUserEvent event) throws Exception {
        callScriptListeners(event);
    }
    
}
