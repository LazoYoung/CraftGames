package com.github.lazoyoung.LegacyCore.script;

import com.github.lazoyoung.LegacyCore.CraftGames;
import com.github.lazoyoung.LegacyCore.script.event.listener.ScriptEventDispatcher;
import jdk.nashorn.api.scripting.ScriptUtils;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.scheduler.Task;

import javax.script.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

public class Script {
    
    public int id;
    public boolean run;
    private CraftGames plugin;
    private ScriptEngine engine;
    private File file;
    private HashMap<String, String> listeners;
    private List<Task> tasks;
    
    
    private Script(File file) throws ScriptException {
        this.file = file;
        this.plugin = CraftGames.getInstance();
        this.engine = new ScriptEngineManager().getEngineByName("nashorn");
        this.id = new Random().nextInt(1000000);
        this.listeners = new HashMap<>();
        this.tasks = new ArrayList<>();
        this.run = false;
        
        buildScript();
        ScriptRegistry.registerScript(this);
    }
    
    /**
     * Search for the script file under the plugin's config folder.
     * @param fileName Indicates the script file. You may omit filename extension.
     * @param copyIfAbsent Whether to look into the plugin jar to find the file.
     * @return The script instance is null unless the script file were found.
     */
    public static Optional<Script> get(String fileName, boolean copyIfAbsent) {
        Path dir = CraftGames.getInstance().getConfigDir();
        dir.toFile().mkdirs();
        
        if(!fileName.contains(".")) {
            fileName = fileName.concat(".js");
        }
        
        File file = dir.resolve(fileName).toFile();
        Script script = null;
        
        if(file.isFile()) {
            try {
                script = new Script(file);
            } catch (ScriptException e) {
                e.printStackTrace();
                script = null;
            }
        }
        else if(copyIfAbsent) {
            Optional<Asset> asset = Sponge.getAssetManager().getAsset(CraftGames.getInstance(), fileName);
            
            if(asset.isPresent()) {
                try {
                    asset.get().copyToDirectory(dir);
                    return get(fileName, false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        return Optional.ofNullable(script);
    }
    
    /**
     * While the execution, script may register some tasks and event listeners_ by itself.
     * @throws ScriptException Script syntax is invalid.
     * @throws FileNotFoundException File can't be opened or is missing.
     */
    public void run() throws ScriptException, FileNotFoundException {
        run = true;
        engine.eval(new FileReader(file));
    }
    
    public String getFilename() {
        return file.getName();
    }
    
    public String getIdentifier() {
        return this.getFilename() + id;
    }
    
    public void unregisterListeners() {
        final Script script = this;
        listeners.keySet().forEach(e -> {
            ScriptEventDispatcher dispatcher = ScriptRegistry.getEventDispatcher(e);
            
            if(dispatcher != null) {
                dispatcher.unregisterScript(script);
            }
        });
        listeners.clear();
    }
    
    public void unregisterTasks() {
        for(Task task : tasks) {
            task.cancel();
        }
        
        tasks.clear();
    }
    
    public void callListener(String eventName, Event event) throws ScriptException, NoSuchMethodException {
        String function = listeners.get(eventName);
        
        if(function != null) {
            ((Invocable) engine).invokeFunction(function, event);
        }
    }
    
    private void registerListener(String eventType, String function) {
        ScriptEventDispatcher dispatcher = ScriptRegistry.getEventDispatcher(eventType);
        
        if(dispatcher != null) {
            dispatcher.registerScript(this);
            listeners.put(eventType, function);
            plugin.getLogger().debug("Registered " + eventType + " with function " + function);
        }
    }
    
    private void registerDelayedTask(Runnable runnable, int ticks) {
        Task task = buildTask(runnable).delayTicks((long) ticks).submit(plugin);
        tasks.add(task);
    }
    
    private Task.Builder buildTask(Runnable runnable) {
        return Task.builder().execute(runnable);
    }
    
    private void buildScript() throws ScriptException {
        ScriptContext context = new SimpleScriptContext();
        Bindings bindings = new SimpleBindings();
    
        // Util Classes
        bindings.put("Text", engine.eval("Java.type('org.spongepowered.api.text.Text')"));
    
        // Util Methods
        bindings.put("registerDelayedTask", (BiConsumer<Runnable, Integer>) this::registerDelayedTask);
        bindings.put("registerListener", (BiConsumer<String, String>) this::registerListener);
        bindings.put("getEvent", (Function<String, Object>) this::getEvent);
        bindings.put("convertEvent", (BiFunction<Event, Object, Event>) this::convertEvent);
    
        // Event types
        bindings.put("TargetEntityEvent", "TargetEntityEvent");
        bindings.put("TargetBlockEvent", "TargetBlockEvent");
        bindings.put("TargetTileEntityEvent", "TargetTileEntityEvent");
        bindings.put("TargetUserEvent", "TargetUserEvent");
    
        context.setBindings(engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE), ScriptContext.ENGINE_SCOPE);
        context.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
        engine.setContext(context);
    }
    
    private Object getEvent(String event) {
        if(!event.startsWith("org.")) {
            event = "org.spongepowered.api.event." + event;
        }
        
        try {
            return engine.eval("Java.type('" + event + "')");
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    private Event convertEvent(Event event, Object target) {
        return (Event) ScriptUtils.convert(event, target);
    }
    
}
