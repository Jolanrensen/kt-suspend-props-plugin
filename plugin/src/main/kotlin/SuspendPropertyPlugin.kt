package nl.jolanrensen.suspendPropertyPlugin

import arrow.meta.CliPlugin
import arrow.meta.Meta
import arrow.meta.Plugin
import arrow.meta.invoke
import arrow.meta.phases.CompilerContext
import arrow.meta.phases.codegen.ir.valueArguments
import arrow.meta.quotes.*
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertySetterDescriptorImpl
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.contracts.ExperimentalContracts
import kotlin.reflect.KClass

const val debug = true

class SuspendPropertyPlugin : Meta {
    @ExperimentalContracts
    override fun intercept(ctx: CompilerContext): List<Plugin<CompilerContext>> = listOf(suspendProperty)
}


@OptIn(ExperimentalStdlibApi::class)
val Meta.suspendProperty: CliPlugin
    get() = "SuspendProperty" {
        meta(
            enableIr(),

            property(this, { modifierList?.text?.contains("suspend") ?: false }) { prop: KtProperty ->

                if (delegate.toString() != "") throw IllegalArgumentException("Delegate suspend properties are not supported yet.")
                if (initializer.toString() != "") throw IllegalArgumentException("Suspend property initializers are not supported yet.")

                val vis = visibility?.toString()?.let { "$it " } ?: ""
                val mod = modality?.toString()?.let { "$it " } ?: ""

                Transform.replace(
                    replacing = prop,
                    newDeclarations = buildList {

                        this += """
                            |${if (debug) META_DEBUG_COMMENT else ""}      
                            |annotation class _SuspendProp
                        """.trimMargin().trim().`class`

                        this += """
                            |${if (debug) META_DEBUG_COMMENT else ""}      
                            |@_SuspendProp
                        """.trimMargin().trim().annotationEntry

                        // add original property with exceptions so that ::references still work
                        this += run {
                            """
                                |${if (debug) META_DEBUG_COMMENT else ""}
                                |$modality $visibility $valOrVar $name $returnType $initializer
                                |   ${if (getter.toString() == "" || value.hasInitializer()) "" else "get() = throw Exception()"}
                                |   ${if (setter.toString() == "" || value.hasInitializer()) "" else "set${setter.`(params)`} = throw Exception()"}
                            """.trimMargin().trim().property
                        }

                        this += getter.run {
                            val bodyWithoutCommentsAndWhiteSpace = bodyExpression.toString()
                                .withoutComments()
                                .withoutWhitespace()

                            """
                                |${if (debug) META_DEBUG_COMMENT else ""}
                                |$mod${vis}suspend fun _get${prop.name!!.capitalize()}()$returnType ${
                                if (bodyWithoutCommentsAndWhiteSpace.first() == '{') "$bodyExpression" else "= $bodyExpression"
                            }
                            """.trimMargin().trim().function
                        }

                        if (value.isVar) this += setter.run {
                            val bodyWithoutCommentsAndWhiteSpace = bodyExpression.toString()
                                .withoutComments()
                                .withoutWhitespace()

                            """
                                |${if (debug) META_DEBUG_COMMENT else ""}
                                |$mod${vis}suspend fun _set${prop.name!!.capitalize()}${
                                ScopedList(
                                    prefix = "(",
                                    value = value?.valueParameters ?: emptyList(),
                                    postfix = ")",
                                    forceRenderSurroundings = true,
                                    transform = { "${it.name}$returnType" },
                                )
                            } ${
                                if (bodyWithoutCommentsAndWhiteSpace.first() == '{') "$bodyExpression" else "= $bodyExpression"
                            }
                            """.trimMargin().trim().function
                        }

                    }
                )
            },

//            irFile { file ->
//                println("old file: " + file.dump())
//                file
//            },

            IrGeneration { _, moduleFragment, pluginContext ->
                moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {

                    override fun visitCall(expression: IrCall): IrExpression {
                        println("call: " + expression.dump())

                        val accessorFunctionName = expression.symbol.toString()

                        val result = when {

                            "<get-" in accessorFunctionName -> {
                                if ((expression.symbol.descriptor as? PropertyGetterDescriptorImpl)
                                        ?.correspondingProperty
                                        ?.toString()
                                        ?.startsWith("@_SuspendProp") == true
                                ) {
//                                    val propName = (
//                                            (expression.symbol.signature as IdSignature.AccessorSignature)
//                                                .propertySignature as IdSignature.PublicSignature
//                                            )
//                                        .declarationFqn
//                                        .asString()
                                    val propName = (expression.symbol.descriptor as PropertyGetterDescriptorImpl)
                                        .correspondingProperty
                                        .name
                                        .asString()

                                    DeclarationIrBuilder(pluginContext, expression.symbol)
                                        .irCall(
                                            pluginContext
                                                .referenceFunctions(FqName("_get${propName.capitalize()}"))
                                                .first()
                                        )
                                } else expression
                            }

                            "<set-" in accessorFunctionName -> {
                                if ((expression.symbol.descriptor as? PropertySetterDescriptorImpl)
                                        ?.correspondingProperty
                                        ?.toString()
                                        ?.startsWith("@_SuspendProp") == true
                                ) {
                                    val propName = (expression.symbol.descriptor as PropertySetterDescriptorImpl)
                                        .correspondingProperty
                                        .name
                                        .asString()


                                    DeclarationIrBuilder(pluginContext, expression.symbol)
                                        .irCall(
                                            pluginContext
                                                .referenceFunctions(FqName("_set${propName.capitalize()}"))
                                                .first()
                                        ).apply {
                                            putValueArgument(0, expression.valueArguments.first().second)
                                        }
                                } else expression
                            }
                            else -> expression
                        }

                        expression.transformChildrenVoid(this)
                        return result
                    }
                })
            },

//            irFile { file ->
//                println("new File: " + file.dump())
//                file
//            },

        )
    }


private fun PsiElement.runOnAllChildren(block: (PsiElement) -> Unit) {
    children.forEach { it.runOnAllChildren(block) }
}

private fun String.withoutComments(): String =
    replace("((['\"])(?:(?!\\2|\\\\).|\\\\.)*\\2)|//[^\\n]*|/\\*(?:[^*]|\\*(?!/))*\\*/".toRegex(), "")

private fun String.withoutWhitespace(): String = replace("\\s".toRegex(), "")

fun KtAnnotated.hasAnnotation(annotationClass: KClass<*>): Boolean = hasAnnotation(annotationClass.simpleName!!)

fun KtAnnotated.hasAnnotation(annotation: String): Boolean =
    annotationEntries.any {
        it.typeReference
            ?.typeElement
            ?.safeAs<KtUserType>()
            ?.referencedName == annotation
    }
