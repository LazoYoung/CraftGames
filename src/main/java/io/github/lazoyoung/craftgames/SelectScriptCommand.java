package io.github.lazoyoung.craftgames;

import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;

import java.util.Optional;

public class SelectScriptCommand implements CommandExecutor {
    
    @Override
    public CommandResult execute(CommandSource src, CommandContext args) {
        String fileName = getParsedFilename(args);
        String userId = Script.getSelectorID(src);
        boolean copyIfAbsent = args.hasAny("c");
        
        if(userId == null) {
            src.sendMessage(Text.of("The CommandSource of you are not supported to use this command."));
            return CommandResult.empty();
        }
        
        computeSelection(userId, fileName, copyIfAbsent, src);
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
    
    private void computeSelection(String userId, String fileName, boolean copyIfAbsent, CommandSource src) {
        Optional<Script> optScript = Script.get(fileName, copyIfAbsent);
        Optional<Script> oldSel = Script.getCommandSelection(userId);
        
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
            Script.setCommandSelection(userId, optScript.get());
            src.sendMessage(Text.of("Selected script: " + fileName));
            return;
        }
        
        oldSel.ifPresent(script -> src.sendMessage(Text.of("Current selection: " + script.getFilename())));
    }
    
}
