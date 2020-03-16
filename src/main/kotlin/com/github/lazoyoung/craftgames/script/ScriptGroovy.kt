package com.github.lazoyoung.craftgames.script

import com.github.lazoyoung.craftgames.Main
import groovy.lang.GroovyShell
import groovy.lang.Script
import org.bukkit.command.CommandSender
import org.codehaus.groovy.control.CompilerConfiguration
import org.kohsuke.groovy.sandbox.GroovyValueFilter
import org.kohsuke.groovy.sandbox.SandboxTransformer
import java.io.File

class ScriptGroovy(file: File, sender: CommandSender?) : ScriptBase(file, sender) {
    private val shell: GroovyShell
    private val filter: GroovyValueFilter
    private var script: Script? = null

    init {
        val shellConfig = CompilerConfiguration().addCompilationCustomizers(SandboxTransformer())
        shellConfig.sourceEncoding = Main.charset.name()
        filter = object : GroovyValueFilter() {
            // TODO Intercept abusive actions
        }
        shell = GroovyShell(shellConfig)
    }

    override fun setVariable(name: String, obj: Any) {
        shell.setProperty(name, obj)
    }

    override fun parse() {
        script = shell.parse(reader, name)
    }

    override fun execute() {
        if (script == null)
            parse()

        filter.register()
        script?.run()
        filter.unregister()
    }
}