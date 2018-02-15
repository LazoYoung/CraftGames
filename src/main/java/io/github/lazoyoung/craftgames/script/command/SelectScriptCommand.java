package io.github.lazoyoung.craftgames.script.command;

import io.github.lazoyoung.craftgames.script.Script;
import io.github.lazoyoung.craftgames.script.ScriptRegistration;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;

import java.util.Optional;

public class SelectScriptCommand extends ScriptCommand implements CommandExecutor {
    
    @Override
    public CommandResult execute(CommandSource src, CommandContext args) {
        String fileName = getParsedFilename(args);
        boolean copyIfAbsent = args.hasAny("c");
        String selector = ScriptRegistration.getSelectorID(src);
        
        if(selector == null) {
            notifyInvalidSelector(src);
            return CommandResult.empty();
        }
        
        computeSelection(selector, fileName, copyIfAbsent, src);
        return CommandResult.success();
    }
    
    public CommandSpec get() {
        return CommandSpec.builder()
                .description(Text.of("Select a script with given filename."))
                .extendedDescription(Text.of("Append -c to copy the file from plugin jar."))
                .permission("craftgames.script.select")
                .arguments(
                        GenericArguments.string(Text.of("fileName")),
                        GenericArguments.flags().flag("c").buildWith(GenericArguments.none())
                )
                .executor(this)
                .build();
                
    }
    
    private String getParsedFilename(CommandContext context) {
        String str = context.<String> getOne("fileName").get();
        return str.contains(".") ? str : str.concat(".js");
    }
    
    private void computeSelection(String selector, String fileName, boolean copyIfAbsent, CommandSource src) {
        Optional<Script> optScript = Script.get(fileName, copyIfAbsent);
        Optional<Script> oldSel = ScriptRegistration.getSelection(selector);
        
        if(oldSel.isPresent()) {
            if(oldSel.get().getFilename().equals(fileName)) {
                src.sendMessage(Text.of("You have already selected this script."));
                return;
            }
        }
        
        if(!optScript.isPresent()) {
            src.sendMessage(Text.of("That file can\'t be found."));
        }
        else {
            Script.setCommandSelection(src, optScript.get());
            src.sendMessage(Text.of("Selected script: " + fileName));
            return;
        }
        
        oldSel.ifPresent(script -> src.sendMessage(Text.of("Current selection: " + script.getFilename())));
    }
    
}
