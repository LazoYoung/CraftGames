package com.github.lazoyoung.LegacyCore.script;

import com.github.lazoyoung.LegacyCore.script.event.listener.ScriptEventDispatcher;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.entity.living.player.Player;

import java.util.HashMap;
import java.util.Optional;

public class ScriptRegistry {
    
    private static HashMap<String, Script> scripts = new HashMap<>();
    private static HashMap<String, ScriptEventDispatcher> eventDispatchers = new HashMap<>();
    private static HashMap<String, String> selections = new HashMap<>();
    
    public static void registerScript(Script script) {
        scripts.put(script.getIdentifier(), script);
    }
    
    public static void unregisterScript(Script script) {
        scripts.remove(script.getIdentifier());
    }
    
    public static void selectScript(Script script, CommandSource selector) {
        selections.put(getSelectorID(selector), script.getIdentifier());
    }
    
    public static void addEventDispatcher(ScriptEventDispatcher listener) {
        eventDispatchers.put(listener.getEventName(), listener);
    }
    
    public static ScriptEventDispatcher getEventDispatcher(String event) {
        return eventDispatchers.get(event);
    }
    
    public static String getSelectorID(CommandSource src) {
        if(src instanceof Player) {
            return ((Player) src).getUniqueId().toString();
        }
        else if(src instanceof ConsoleSource) {
            return "console";
        }
        
        return null;
    }
    
    public static Script getScript(String scriptId) {
        return scripts.get(scriptId);
    }
    
    public static Optional<Script> getSelection(String selectorId) {
        String scriptId = selections.get(selectorId);
        
        if(scriptId != null) {
            return Optional.ofNullable(getScript(scriptId));
        }
        
        return Optional.empty();
    }
    
}
