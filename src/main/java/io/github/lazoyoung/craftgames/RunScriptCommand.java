package io.github.lazoyoung.craftgames;

import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;

import javax.script.ScriptException;
import java.io.FileNotFoundException;
import java.util.Optional;

class RunScriptCommand implements CommandExecutor {
    
    @Override
    public CommandResult execute(CommandSource src, CommandContext args) {
        Optional<String> fileNameArg = args.getOne("fileName");
        
        if(!fileNameArg.isPresent()) {
            return CommandResult.empty();
        }
        
        String fileName = fileNameArg.get();
        Optional<Script> optScript = Script.get(fileName, true);
        
        if(optScript.isPresent()) {
            try {
                optScript.get().run();
                src.sendMessage(Text.of("Successfully executed the script."));
            } catch (ScriptException e) {
                e.printStackTrace();
                src.sendMessage(Text.of(fileName + " has an error at line " + e.getLineNumber()));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                src.sendMessage(Text.of(fileName + " can't be executed because it's missing."));
            }
        }
        else {
            src.sendMessage(Text.of("File \'" + fileName + "\' was not found."));
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