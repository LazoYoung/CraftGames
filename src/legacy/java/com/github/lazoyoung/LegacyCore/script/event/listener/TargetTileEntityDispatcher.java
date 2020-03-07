package com.github.lazoyoung.LegacyCore.script.event.listener;

import org.spongepowered.api.event.block.tileentity.TargetTileEntityEvent;

public class TargetTileEntityDispatcher extends ScriptEventDispatcher<TargetTileEntityEvent> {
    
    public TargetTileEntityDispatcher(String eventName) {
        super(eventName);
    }
    
    @Override
    public void handle(TargetTileEntityEvent event) throws Exception {
        callScriptListeners(event);
    }
    
}
