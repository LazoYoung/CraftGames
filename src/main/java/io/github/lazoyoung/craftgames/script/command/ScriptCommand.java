package io.github.lazoyoung.craftgames.script.command;

import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;

public class ScriptCommand implements CommandExecutor {
    
    @Override
    public CommandResult execute(CommandSource src, CommandContext args) {
        src.sendMessage(Text.of("[CraftGames Script Command Help]\n" +
                "/cg script select - Select a script file.\n" +
                "/cg script run - Execute the selected script.\n" +
                "/cg script discard - Discard the selected script.\n"
        ));
        return CommandResult.success();
    }
    
    public CommandSpec get() {
        return CommandSpec.builder()
                .description(Text.of("See usage of script commands.."))
                .child(new SelectScriptCommand().get(), "select", "load")
                .child(new RunScriptCommand().get(), "run", "start", "execute")
                .child(new DiscardScriptCommand().get(), "discard", "stop", "disable", "unload")
                .executor(this)
                .build();
    }
    
    void notifyMissingSelection(CommandSource src, String commandSuggest) {
        src.sendMessage(Text.of("Please select a script: " + commandSuggest));
    }
    
    void notifyMissingSelection(CommandSource src) {
        notifyMissingSelection(src, "/cg script select");
    }
    
    void notifyInvalidSelector(CommandSource src) {
        src.sendMessage(Text.of("You can not use this command."));
    }
}
