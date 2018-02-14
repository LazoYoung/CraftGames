package io.github.lazoyoung.craftgames;

import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;

import javax.script.ScriptException;
import java.io.FileNotFoundException;
import java.util.Optional;

class RunScriptCommand implements CommandExecutor {
    
    @Override
    public CommandResult execute(CommandSource src, CommandContext args) {
        Optional<Script> optScript = Script.getCommandSelection(Script.getSelectorID(src));
        
        if(optScript.isPresent()) {
            Script script = optScript.get();
            try {
                script.run();
                src.sendMessage(Text.of("Successfully executed the script."));
            } catch (ScriptException e) {
                e.printStackTrace();
                src.sendMessage(Text.of(script.getFilename() + " has an error at line " + e.getLineNumber()));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                src.sendMessage(Text.of(script.getFilename() + " can't be executed because it's missing."));
            }
        }
        else {
            src.sendMessage(Text.of("You have to select a script: /cg script select"));
        }
        
        return CommandResult.success();
    }
    
    public CommandSpec get() {
        return CommandSpec.builder()
                .description(Text.of("Execute a selected script."))
                .permission("craftgames.script.run")
                .executor(this)
                .build();
    }
}