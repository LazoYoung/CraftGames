package com.github.lazoyoung.craftgames.impl.script.groovy

import com.github.lazoyoung.craftgames.impl.game.module.ModuleService
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.MethodCall
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.transform.stc.AbstractTypeCheckingExtension
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor
import java.net.URI

class GroovyASTExtension(typeCheckingVisitor: StaticTypeCheckingVisitor)
    : AbstractTypeCheckingExtension(typeCheckingVisitor) {

    private var gameScript: GameScriptGroovy? = null

    init {
        super.debug = false
        val uri = URI(typeCheckingVisitor.typeCheckingContext.source.name)
        gameScript = GameScriptGroovy.registry[uri]
    }

    override fun handleUnresolvedVariableExpression(vexp: VariableExpression): Boolean {
        ModuleService.getASTClassNode(vexp.name)?.let {
            storeType(vexp, it)
            setHandled(true)
            return true
        }
        return false
    }

    override fun handleMissingMethod(
            receiver: ClassNode,
            name: String,
            argumentList: ArgumentListExpression,
            argumentTypes: Array<out ClassNode>,
            call: MethodCall
    ): MutableList<MethodNode> {
        val nodeList = ArrayList<MethodNode>()

        gameScript?.printDebug("Resolving method: ${receiver.name}.$name${argumentList.text}")

        loop@ for (methodNode in receiver.getMethods(name)) {
            if (methodNode.parameters.size != argumentTypes.size) {
                continue
            }

            val iter = methodNode.parameters.iterator().withIndex()

            while (iter.hasNext()) {
                val element = iter.next()
                val argType = argumentTypes[element.index]
                val paramType = element.value.type

                if (argType != paramType) {
                    if (!argType.isResolved || !paramType.isResolved)
                        continue@loop

                    try {
                        val argClazz = argType.typeClass
                        val paramClazz = paramType.typeClass

                        if (paramClazz.isAssignableFrom(argClazz)
                                || (isNumber(paramClazz) && isNumber(argClazz)))
                        {
                            // Numeric arguments are resolved dynamically.
                            nodeList.add(makeDynamic(call))
                            break@loop
                        }
                    } catch (e: Exception) {
                        continue@loop
                    }
                }
            }
        }

        return nodeList
    }

    override fun makeDynamic(call: MethodCall, returnType: ClassNode): MethodNode {
        gameScript?.printDebug("Turning " + call.text +" into a dynamic method call returning " + returnType.toString(false))
        return super.makeDynamic(call, returnType)
    }

    override fun makeDynamic(pexp: PropertyExpression, returnType: ClassNode) {
        gameScript?.printDebug("Turning '" + pexp.text + "' into a dynamic property access of type " + returnType.toString(false))
        super.makeDynamic(pexp, returnType)
    }

    override fun makeDynamic(vexp: VariableExpression, returnType: ClassNode) {
        gameScript?.printDebug("Turning '"+vexp.text +"' into a dynamic variable access of type "+returnType.toString(false))
        super.makeDynamic(vexp, returnType)
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun isNumber(clazz: Class<Any>): Boolean {
        if (Class.forName("java.lang.Number").isAssignableFrom(clazz)) {
            return true
        }

        if (clazz.isPrimitive) {
            return when (clazz) {
                java.lang.Byte.TYPE, java.lang.Short.TYPE,
                Integer.TYPE, java.lang.Long.TYPE,
                java.lang.Float.TYPE, java.lang.Double.TYPE
                    -> true
                else
                    -> false
            }
        }

        return false
    }

}