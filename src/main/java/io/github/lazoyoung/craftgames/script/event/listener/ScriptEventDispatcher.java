package io.github.lazoyoung.craftgames.script.event.listener;

import io.github.lazoyoung.craftgames.CraftGames;
import io.github.lazoyoung.craftgames.script.Script;
import io.github.lazoyoung.craftgames.script.ScriptRegistry;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.EventListener;

import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.List;

public abstract class ScriptEventDispatcher<T extends Event> implements EventListener<T> {
    
    protected String eventName;
    private List<String> scripts = new ArrayList<>();
    private CraftGames plugin = CraftGames.getInstance();
    
    
    public ScriptEventDispatcher(String eventName) {
        this.eventName = eventName;
    }
    
    
    public void registerScript(Script script) {
        scripts.add(script.getIdentifier());
    }
    
    public void unregisterScript(Script script) {
        scripts.remove(script.getIdentifier());
    }
    
    public String getEventName() {
        return eventName;
    }
    
    protected void callScriptListeners(Event event) {
        scripts.forEach(s -> {
            Script script = ScriptRegistry.getScript(s);
            
            if(script != null) {
                try {
                    script.callListener(eventName, event);
                } catch (ScriptException e) {
                    e.printStackTrace();
                    plugin.getLogger().error("ScriptException occurred: " + script.getFilename());
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}