package io.github.lazoyoung.craftgames;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.text.Text;

import javax.script.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class Game {
    
    public ScriptCommand scriptCommand;
    private CraftGames plugin;
    private ScriptEngine engine;
    private ScriptContext context;
    private Bindings bindings;
    private Path dir;
    
    public Game() {
        plugin = CraftGames.getInstance();
        dir = plugin.getConfigDir();
        scriptCommand = new ScriptCommand(this);
        engine = new ScriptEngineManager().getEngineByName("nashorn");
        context = new SimpleScriptContext();
        bindings = new SimpleBindings();
    
        try {
            bindings.put("EventListener", engine.eval("Java.type('org.spongepowered.api.event.EventListener')"));
            bindings.put("registerListener", (BiConsumer<Class, EventListener>) this::registerListener);
            bindings.put("getEvent", (Function<String, Class>) this::getEvent);
            context.setBindings(engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE), ScriptContext.ENGINE_SCOPE);
            context.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
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
            engine.eval(new FileReader(file), bindings);
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
    
    @SuppressWarnings("unchecked")
    private void registerListener(Class eventClass, EventListener listener) {
        Sponge.getEventManager().registerListener(CraftGames.getInstance(), eventClass, listener);
    }
    
    private Class getEvent(String eventPath) {
        if(!eventPath.startsWith("org.")) {
            eventPath = "org.spongepowered.api.event." + eventPath;
        }
        
        try {
            return Class.forName(eventPath);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
}

class ScriptCommand implements CommandExecutor {
    
    private Game game;
    
    public ScriptCommand(Game game) {
        this.game = game;
    }
    
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