package io.github.lazoyoung.craftgames;

import jdk.nashorn.api.scripting.ScriptUtils;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.block.TargetBlockEvent;
import org.spongepowered.api.event.block.tileentity.TargetTileEntityEvent;
import org.spongepowered.api.event.entity.TargetEntityEvent;
import org.spongepowered.api.event.user.TargetUserEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import javax.script.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.*;

public class Game {
    
    public ScriptCommand scriptCommand;
    private CraftGames plugin;
    private ScriptEngine engine;
    private Path dir;
    
    public Game() {
        plugin = CraftGames.getInstance();
        dir = plugin.getConfigDir();
        scriptCommand = new ScriptCommand(this);
        engine = new ScriptEngineManager().getEngineByName("nashorn");
        
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
     *
     * @param fileName including filename extension
     * @param copyIfAbsent If file is absent, copy it from plugin.jar if available
     * @return False if file is absent
     * @throws ScriptException may occur if any syntax error exists
     */
    boolean runScript(String fileName, boolean copyIfAbsent) throws ScriptException {
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
    
    private void registerListener(String eventType, String function) {
        EventManager man = Sponge.getEventManager();
        
        // TODO Support TargetInventoryEvent, MessageEvent. Best practice is to support all types.
        switch(eventType) {
            case "TargetEntityEvent":
                EventListener<TargetEntityEvent> listener
                        = event -> handleEvent(function, event, event.getTargetEntity().getLocation());
                man.registerListener(plugin, TargetEntityEvent.class, listener);
                break;
                
            case "TargetBlockEvent":
                EventListener<TargetBlockEvent> listener1
                        =  event -> handleEvent(function, event, event.getTargetBlock().getLocation());
                man.registerListener(plugin, TargetBlockEvent.class, listener1);
                break;
                
            case "TargetTileEntityEvent":
                EventListener<TargetTileEntityEvent> listener2
                        = event -> handleEvent(function, event, event.getTargetTile().getLocation());
                man.registerListener(plugin, TargetTileEntityEvent.class, listener2);
                break;
                
            case "TargetUserEvent":
                EventListener<TargetUserEvent> listener3
                        = event -> handleEvent(function, event, event.getTargetUser().getPlayer().get().getLocation());
                man.registerListener(plugin, TargetUserEvent.class, listener3);
                break;
        }
    }
    
    private void registerDelayedTask(Runnable runnable, int ticks) {
        Task task = registerTask(runnable).delayTicks((long) ticks).submit(plugin);
    }
    
    private Task.Builder registerTask(Runnable runnable) {
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

class ScriptCommand implements CommandExecutor {
    
    private Game game;
    
    public ScriptCommand(Game game) {
        this.game = game;
    }
    
    // TODO Allow reloading scripts.
    @Override
    public CommandResult execute(CommandSource src, CommandContext args) {
        Optional<String> fileNameArg = args.getOne("fileName");
        
        if(!fileNameArg.isPresent()) {
            return CommandResult.empty();
        }
        
        String fileName = fileNameArg.get();
        
        try {
            if(!game.runScript(fileName, true)) {
                src.sendMessage(Text.of("Script file not found: " + fileName));
            }
            else {
                src.sendMessage(Text.of("Executed the script successfully."));
            }
        } catch (ScriptException e) {
            src.sendMessage(Text.of("Script error occurred. Exception stacktrace was printed in console."));
            e.printStackTrace();
        }
        
        return CommandResult.success();
    }
    
    public CommandSpec get() {
        return CommandSpec.builder()
                .description(Text.of("Run a script with given <fileName> for test purpose."))
                .permission("craftgames.runscript")
                .arguments(GenericArguments.onlyOne(GenericArguments.string(Text.of("fileName"))))
                .executor(this)
                .build();
    }
}