package io.github.lazoyoung.craftgames;

import jdk.nashorn.api.scripting.ScriptUtils;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.block.TargetBlockEvent;
import org.spongepowered.api.event.block.tileentity.TargetTileEntityEvent;
import org.spongepowered.api.event.entity.TargetEntityEvent;
import org.spongepowered.api.event.user.TargetUserEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import javax.script.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

public class Script {
    
    private static HashMap<String, Script> cmdSelect = new HashMap<>();
    private CraftGames plugin;
    private ScriptEngine engine;
    private File file;
    private List<EventListener<? extends Event>> listeners;
    private List<Task> tasks;
    
    
    private Script(File file) {
        this.file = file;
        this.plugin = CraftGames.getInstance();
        this.engine = new ScriptEngineManager().getEngineByName("nashorn");
        this.listeners = new ArrayList<>();
        this.tasks = new ArrayList<>();
    
        try {
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
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * This will search for the script file under the plugin's config folder.
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
            script = new Script(file);
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
    
    public static Optional<Script> getCommandSelection(String id) {
        return Optional.ofNullable(cmdSelect.get(id));
    }
    
    public static void setCommandSelection(String id, Script sel) {
        cmdSelect.put(id, sel);
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
    
    /**
     * While the execution, script may register some tasks and event listeners by itself.
     * @throws ScriptException Script syntax is invalid.
     * @throws FileNotFoundException File can't be opened or is missing.
     */
    public void run() throws ScriptException, FileNotFoundException {
        engine.eval(new FileReader(file));
    }
    
    @Deprecated
    boolean runScript(String fileName, boolean copyIfAbsent) throws ScriptException {
        Path dir = CraftGames.getInstance().getConfigDir();
        Path path = dir.resolve(fileName);
        File file = path.toFile();
        dir.toFile().mkdirs();
        
        try {
            engine.eval(new FileReader(file));
        }
        
        catch (FileNotFoundException e) {
            if(copyIfAbsent) {
                Optional<Asset> asset = Sponge.getAssetManager().getAsset(plugin, fileName);
                
                if(asset.isPresent()) {
                    try {
                        asset.get().copyToFile(path);
                        return runScript(fileName, false);
                    } catch (IOException | ScriptException e1) {
                        e1.printStackTrace();
                    }
                }
            }
            
            return false;
        }
        
        return true;
    }
    
    public String getFilename() {
        return file.getName();
    }
    
    public void unregisterListeners() {
        EventManager man = Sponge.getEventManager();
        
        for(EventListener<? extends Event> listener : listeners) {
            man.unregisterListeners(listener);
        }
        
        listeners.clear();
    }
    
    public void unregisterTasks() {
        for(Task task : tasks) {
            task.cancel();
        }
        
        tasks.clear();
    }
    
    private void registerListener(String eventType, String function) {
        EventManager man = Sponge.getEventManager();
        
        // TODO Support TargetInventoryEvent, MessageEvent. Best practice is to support all types.
        switch(eventType) {
            case "TargetEntityEvent":
                EventListener<TargetEntityEvent> listener
                        = event -> handleEvent(function, event, event.getTargetEntity().getLocation());
                man.registerListener(plugin, TargetEntityEvent.class, listener);
                listeners.add(listener);
                break;
            
            case "TargetBlockEvent":
                EventListener<TargetBlockEvent> listener1
                        =  event -> handleEvent(function, event, event.getTargetBlock().getLocation());
                man.registerListener(plugin, TargetBlockEvent.class, listener1);
                listeners.add(listener1);
                break;
            
            case "TargetTileEntityEvent":
                EventListener<TargetTileEntityEvent> listener2
                        = event -> handleEvent(function, event, event.getTargetTile().getLocation());
                man.registerListener(plugin, TargetTileEntityEvent.class, listener2);
                listeners.add(listener2);
                break;
            
            case "TargetUserEvent":
                EventListener<TargetUserEvent> listener3
                        = event -> handleEvent(function, event, event.getTargetUser().getPlayer().get().getLocation());
                man.registerListener(plugin, TargetUserEvent.class, listener3);
                listeners.add(listener3);
                break;
        }
    }
    
    private void registerDelayedTask(Runnable runnable, int ticks) {
        Task task = buildTask(runnable).delayTicks((long) ticks).submit(plugin);
        tasks.add(task);
    }
    
    private Task.Builder buildTask(Runnable runnable) {
        return Task.builder().execute(runnable);
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
    
    private void handleEvent(String function, Event event, Location<World> loc) {
        handleEvent(function, event, Optional.of(loc));
    }
    
    private void handleEvent(String function, Event event, Optional<Location<World>> loc) {
        
        // TODO Handle the event unless the location points outside of the game.
        if(!loc.isPresent()) {
            return;
        }
        
        Invocable inv = (Invocable) engine;
        
        try {
            inv.invokeFunction(function, event);
        } catch (ScriptException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }
    
}
