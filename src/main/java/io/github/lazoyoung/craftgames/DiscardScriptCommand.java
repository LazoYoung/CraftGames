package io.github.lazoyoung.craftgames;

import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;

import java.util.Optional;

public class DiscardScriptCommand extends ScriptCommand implements CommandExecutor {
    
    @Override
    public CommandResult execute(CommandSource src, CommandContext args) {
        String selector = ScriptRegistration.getSelectorID(src);
        
        if(selector == null) {
            notifyInvalidSelector(src);
            return CommandResult.empty();
        }
        
        Optional<Script> optScript = ScriptRegistration.getSelection(selector);
        
        if(!optScript.isPresent()) {
            notifyMissingSelection(src);
            return CommandResult.success();
        }
        
        Script script = optScript.get();
        
        if(!script.run) {
            src.sendMessage(Text.of("The script is not active."));
            return CommandResult.success();
        }
        
        script.unregisterListeners();
        script.unregisterTasks();
        ScriptRegistration.unregisterScript(script);
        src.sendMessage(Text.of("Discarded the script."));
        return CommandResult.success();
    }
    
    public CommandSpec get() {
        return CommandSpec.builder()
                .description(Text.of("Discards a script with its variables, tasks, and event listeners."))
                .permission("craftgames.script.discard")
                .executor(this)
                .build();
    }
}
