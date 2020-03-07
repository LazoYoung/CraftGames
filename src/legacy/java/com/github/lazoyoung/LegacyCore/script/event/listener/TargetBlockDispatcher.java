package com.github.lazoyoung.LegacyCore.script.event.listener;

import org.spongepowered.api.event.block.TargetBlockEvent;

public class TargetBlockDispatcher extends ScriptEventDispatcher<TargetBlockEvent> {
    
    public TargetBlockDispatcher(String eventName) {
        super(eventName);
    }
    
    @Override
    public void handle(TargetBlockEvent event) throws Exception {
        callScriptListeners(event);
    }
    
}
