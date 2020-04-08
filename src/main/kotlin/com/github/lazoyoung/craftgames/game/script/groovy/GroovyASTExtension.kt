package com.github.lazoyoung.craftgames.game.script.groovy

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.game.module.Module
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.MethodCall
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.transform.stc.AbstractTypeCheckingExtension
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor

class GroovyASTExtension(typeCheckingVisitor: StaticTypeCheckingVisitor)
    : AbstractTypeCheckingExtension(typeCheckingVisitor) {

    init {
        super.debug = Main.getConfig()?.getBoolean("script.verbose", false) ?: false
    }

    override fun handleUnresolvedVariableExpression(vexp: VariableExpression): Boolean {
        Module.getASTClassNode(vexp.name)?.let {
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
                    try {
                        val argClazz = argType.typeClass
                        val paramClazz = paramType.typeClass

                        if (paramClazz.isAssignableFrom(argClazz)
                                || (isNumber(paramClazz) && isNumber(argClazz)))
                        {
                            nodeList.add(makeDynamic(call, receiver))
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