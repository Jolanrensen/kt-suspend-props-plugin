package nl.jolanrensen.suspendPropertyPlugin

import arrow.meta.CliPlugin
import arrow.meta.Meta
import arrow.meta.invoke
import arrow.meta.phases.CompilerContext
import arrow.meta.phases.codegen.ir.IRGeneration
import arrow.meta.phases.codegen.ir.valueArguments
import arrow.meta.quotes.META_DEBUG_COMMENT
import arrow.meta.quotes.ScopedList
import arrow.meta.quotes.Transform
import arrow.meta.quotes.nameddeclaration.stub.typeparameterlistowner.NamedFunction
import arrow.meta.quotes.nameddeclaration.stub.typeparameterlistowner.Property
import arrow.meta.quotes.quote
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertySetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrSymbolDeclaration
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.contracts.ExperimentalContracts
import kotlin.reflect.KClass

const val debug = true

class SuspendPropertyPlugin : Meta /*, MetaIde todo breaks all*/ {

    @ExperimentalContracts
    override fun intercept(ctx: CompilerContext): List<CliPlugin> = listOf(
        // higherKindedTypes2,
        // typeClasses,
        // comprehensions,
        // lenses,
//        typeProofs,
//        optics,
        suspendProperty,
    )

//    @ExperimentalContracts
//    override fun intercept(ctx: IdeContext): List<IdePlugin> = listOf(
//        initialIdeSetUp,
//        quotes,
//        typeProofsIde
//    ) + suspendPropertyIde
}

@OptIn(ExperimentalStdlibApi::class)
val Meta.suspendProperty: CliPlugin
    get() = "SuspendProperty" {
        meta(
            enableIr(),

            // TODO, throws: java.lang.IllegalStateException: Unable to collect additional sources in reasonable number of iterations
//            additionalSources { collection, _, _ ->
//                 (Transform.newSources<KtClass>(
//                    "annotation class _SuspendProp".file("_SuspendProp")
//                ) as Transform.NewSource<KtClass>)
//                    .newSource()
//                    .map { it.first }
//            },

            quote<KtElement>(
                ctx = this,
                match = {
                    when (this) {
                        is KtNamedFunction -> shouldRewriteSuspendOperatorFunsQuote()
                        is KtProperty -> shouldRewriteSuspendPropsQuote()
                        else -> false
                    }
                }
            ) {
                when (it) {
                    is KtNamedFunction -> rewriteSuspendOperatorFunsQuote(it, NamedFunction(it))
                    is KtProperty ->
                        if (it.hasDelegate()) rewriteDelegatedSuspendPropQuote(it, Property(it))
                        else rewriteSuspendPropsQuote(it, Property(it))
                    else -> Transform.empty
                }
            },


            irFile { file ->
                println("old file: " + file.dump())
                file
            },

            manipulateIR(),

            irFile { file ->
                println("new File: " + file.dump())
                file
            },

            )
    }

private fun KtProperty.shouldRewriteSuspendPropsQuote(): Boolean =
    (modifierList?.text?.contains("suspend") ?: false)
            || (getter?.modifierList?.text?.contains("suspend") ?: false)
            || (setter?.modifierList?.text?.contains("suspend") ?: false)

/**
 * Converts something like
 * ```kotlin
 * suspend var test: Int
 *  get() { ... }
 *  set(value) { ... }
 * ```
 * into
 * ```kotlin
 * @SuspendProp
 * var test: Int
 *  get() = throw IllegalArgumentException(...)
 *  set(value) = throw IllegalArgumentException(...)
 *
 * suspend fun _suspendProp_getTest(): Int { ... }
 *
 * suspend fun _suspendProp_setTest(value: Int) { ... }
 * ```
 */
@OptIn(ExperimentalStdlibApi::class)
private fun CompilerContext.rewriteSuspendPropsQuote(
    prop: KtProperty,
    property: Property,
): Transform<KtProperty> = property.run {
    if (delegate.toString() != "") throw IllegalArgumentException("Wrong function, use [rewriteDelegatedSuspendPropQuote].")
    if (returnType.toString()
            .isBlank()
    ) throw IllegalStateException("Please state the return type of suspend properties explicitly.")
    if (initializer.toString() != "") throw IllegalArgumentException("Suspend property initializers are not supported yet.")

    val vis = visibility?.toString()?.let { "$it " } ?: ""
    val mod = modality?.toString()?.let { "$it " } ?: ""

    val propertyIsSuspend = prop.modifierList?.text?.contains("suspend") ?: false

    val hasGetter = getter.value != null
    val hasSetter = setter.value != null

    val getterIsSuspend =
        propertyIsSuspend || prop.getter?.modifierList?.text?.contains("suspend") ?: false
    val setterIsSuspend =
        propertyIsSuspend || prop.setter?.modifierList?.text?.contains("suspend") ?: false

    println("hasGetter: $hasGetter, hasSetter: $hasSetter, getterIsSuspend: $getterIsSuspend, setterIsSuspend: $setterIsSuspend")


    Transform.replace(
        replacing = prop,
        newDeclarations = buildList {

//                        TODO, this becomes an error when there are multiple suspend properties. Must be added only once to a file
//                        this += """
//                            |${if (debug) META_DEBUG_COMMENT else ""}
//                            |annotation class _SuspendProp
//                        """.trimMargin().trim().`class`

            this += """
                    |${if (debug) META_DEBUG_COMMENT else ""}
                    |@SuspendProp
                    """.trimMargin().trim().annotationEntry

            // add original property with exceptions so that ::references still work
            this += """
                    |${if (debug) META_DEBUG_COMMENT else ""}
                    |$modality $visibility $valOrVar $name $returnType $initializer
                    |   ${if (getter.toString() == "" || value.hasInitializer()) "" else "get() = throw IllegalStateException(\"This call is replaced with _suspendProp_get${prop.name!!.capitalize()}() at compile time.\")"}
                    |   ${if (setter.toString() == "" || value.hasInitializer()) "" else "set${setter.`(params)`} = throw IllegalStateException(\"This call is replaced with _suspendProp_set${prop.name!!.capitalize()}() at compile time.\")"}
                    """.trimMargin().trim().property


            if (hasGetter)
                this += getter.run {
                    val bodyWithoutCommentsAndWhiteSpace = bodyExpression.toString()
                        .withoutComments()
                        .withoutWhitespace()

                    val sus = if (getterIsSuspend) "suspend " else ""

                    """
                    |${if (debug) META_DEBUG_COMMENT else ""}
                    |$mod$vis${sus}fun _suspendProp_get${prop.name!!.capitalize()}()$returnType ${if (bodyWithoutCommentsAndWhiteSpace.first() == '{') "$bodyExpression" else "= $bodyExpression"}
                    """.trimMargin().trim().function
                }

            if (hasSetter)
                this += setter.run {
                    val bodyWithoutCommentsAndWhiteSpace = bodyExpression.toString()
                        .withoutComments()
                        .withoutWhitespace()

                    val sus = if (setterIsSuspend) "suspend " else ""
                    val params = ScopedList(
                        value = value?.valueParameters ?: emptyList(),
                        prefix = "(",
                        postfix = ")",
                        forceRenderSurroundings = true,
                    ) { "${it.name}$returnType" }

                    """
                    |${if (debug) META_DEBUG_COMMENT else ""}
                    |$mod$vis${sus}fun _suspendProp_set${prop.name!!.capitalize()}$params ${if (bodyWithoutCommentsAndWhiteSpace.first() == '{') "$bodyExpression" else "= $bodyExpression"}
                    """.trimMargin().trim().function
                }

        }
    )
}

/**
 * Converts something like
 * ```kotlin
 * suspend var c by Test()
 * ```
 * into
 * ```kotlin
 * val _suspendProp_c = Test()
 *
 * @SuspendProp
 * var c: Int
 *     get() = throw IllegalStateException("This call is replaced with _suspendProp_getC() at compile time.")
 *     set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setC() at compile time.")
 *
 * suspend fun _suspendProp_getC(): Int = suspendProp_c._suspendProp_getValue(null, ::c)
 * suspend fun _suspendProp_setC(value: Int) = suspendProp_c._suspendProp_setValue(null, ::c, value)
 * ```
 */
@OptIn(ExperimentalStdlibApi::class)
private fun CompilerContext.rewriteDelegatedSuspendPropQuote(
    prop: KtProperty,
    property: Property,
): Transform<KtProperty> = property.run {
    if (delegate.toString() == "") throw IllegalArgumentException("This suspend property does not have a delegate")
    if (returnType.toString()
            .isBlank()
    ) throw IllegalStateException("Please state the return type of suspend properties explicitly.")
    if (prop.isLocal) throw IllegalStateException("Local suspend delegate properties are not supported. Move it outside this body/function.")


    val vis = visibility?.toString()?.let { "$it " } ?: ""
    val mod = modality?.toString()?.let { "$it " } ?: ""

    val hasGetter = true
    val hasSetter = valOrVar.asString() == "var"

    Transform.replace(
        replacing = prop,
        newDeclarations = buildList {

            this += """
                    |${if (debug) META_DEBUG_COMMENT else ""}
                    |$mod${vis}val _suspendProp_$name = $delegateExpressionOrInitializer
                    """.trimMargin().trim().property

            this += """
                    |${if (debug) META_DEBUG_COMMENT else ""}
                    |@SuspendProp
                    """.trimMargin().trim().annotationEntry

            this += """
                    |${if (debug) META_DEBUG_COMMENT else ""}
                    |$modality $visibility $valOrVar $name $returnType
                    |    ${if (hasGetter) "get() = throw IllegalStateException(\"This call is replaced with _suspendProp_get${prop.name!!.capitalize()}() at compile time.\")" else ""}
                    |    ${if (hasSetter) "set(value) = throw IllegalStateException(\"This call is replaced with _suspendProp_set${prop.name!!.capitalize()}() at compile time.\")" else ""}
                    """.trimMargin().trim().property

            if (hasGetter)
                this += """
                        |${if (debug) META_DEBUG_COMMENT else ""}
                        |$mod${vis}suspend fun _suspendProp_get${prop.name!!.capitalize()}()$returnType = _suspendProp_$name._suspendProp_getValue(null, ::$name)
                        """.trimMargin().trim().function

            if (hasSetter)
                this += """
                        |${if (debug) META_DEBUG_COMMENT else ""}
                        |$mod${vis}suspend fun _suspendProp_set${prop.name!!.capitalize()}(value$returnType) = _suspendProp_$name._suspendProp_setValue(null, ::$name, value)
                        """.trimMargin().trim().function
        },

        )
}

private fun KtNamedFunction.shouldRewriteSuspendOperatorFunsQuote(): Boolean =
    modifierList?.text?.let { "suspend" in it && "operator" in it } ?: false

@OptIn(ExperimentalStdlibApi::class)
private fun CompilerContext.rewriteSuspendOperatorFunsQuote(
    fn: KtNamedFunction,
    namedFunction: NamedFunction,
): Transform<KtNamedFunction> = namedFunction.run {

    val vis = visibility?.toString()?.let { "$it " } ?: ""
    val mod = modality?.toString()?.let { "$it " } ?: ""

    val bodyWithoutCommentsAndWhiteSpace = fn.bodyExpression?.text
        ?.withoutComments()
        ?.withoutWhitespace()
        ?: TODO("suspend operator fun without body?")


    Transform.replace(
        replacing = fn,
        newDeclarations = buildList {
            this += """
                    |${if (debug) META_DEBUG_COMMENT else ""}
                    |@SuspendProp
                    """.trimMargin().trim().annotationEntry

            val ret = if (returnType.isEmpty()) ": Unit" else returnType.toString()

            this += """
                    |${if (debug) META_DEBUG_COMMENT else ""}
                    |$mod${vis}operator fun $name$`(params)`$ret = throw IllegalStateException("This call is replaced with _suspendProp_$name() at compile time.")
                    """.trimMargin().trim()/*.also { println(it) }*/.function

            this += """
                    |${if (debug) META_DEBUG_COMMENT else ""}
                    |$mod${vis}suspend fun _suspendProp_$name$`(params)`$returnType ${if (fn.hasBlockBody()) fn.bodyExpression!!.text else "= ${fn.bodyExpression!!.text}"}
                    """.trimMargin().trim()/*.also { println(it) }*/.function
        }
    )
}

private fun Meta.manipulateIR(): IRGeneration = IrGeneration { _, moduleFragment, pluginContext ->
    moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {

        override fun visitCall(expression: IrCall): IrExpression {
//            println("call: " + expression.dump())

            val accessorFunctionName = expression.symbol.toString()

            // TODO split off into different functions
            val result = when {

                expression.symbol.descriptor.name.asString() == "getValue" -> {
                    if ((expression.symbol.descriptor as? SimpleFunctionDescriptorImpl)
                            ?.toString()
                            ?.startsWith("@SuspendProp") == true
                    ) {

                        expression // TODO

                    } else expression
                }

                "<get-" in accessorFunctionName -> {
                    if ((expression.symbol.descriptor as? PropertyGetterDescriptorImpl)
                            ?.correspondingProperty
                            ?.toString()
                            ?.startsWith("@SuspendProp") == true
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
//
//                        DeclarationIrBuilder(pluginContext, expression.symbol)
//                            .irCall(
//                                pluginContext
//                                    .referenceFunctions(FqName("_suspendProp_get${propName.capitalize()}"))
//                                    .first()
//                            )

//                        val parent =
//                            expression.symbol.descriptor
//                                .let { it as PropertyGetterDescriptorImpl }
//                                .correspondingProperty
//                                .let { pluginContext.symbolTable.referenceProperty(it) }
//                                .owner
//                                .parent
//                                .let { it as IrSymbolDeclaration<*> }
//                                .symbol
//                                .owner



                        DeclarationIrBuilder(pluginContext, expression.symbol)
                            .irCall(
                                pluginContext
                                    .referenceFunctions(FqName("_suspendProp_get${propName.capitalize()}"))
                                    .first()
                            )
                    } else expression
                }

                "<set-" in accessorFunctionName -> {
                    if ((expression.symbol.descriptor as? PropertySetterDescriptorImpl)
                            ?.correspondingProperty
                            ?.toString()
                            ?.startsWith("@SuspendProp") == true
                    ) {

                        val propName = (expression.symbol.descriptor as PropertySetterDescriptorImpl)
                            .correspondingProperty
                            .name
                            .asString()

                        if (
                            (expression.symbol.descriptor as PropertySetterDescriptorImpl)
                                .correspondingProperty
                                .isDelegated // TODO maybe use another way to check this
                        ) {

//                            DeclarationIrBuilder(pluginContext, expression.symbol)
//                                .irCall(
//                                    pluginContext
//                                        .referenceFunctions(FqName("_suspendProp_set${propName.capitalize()}"))
//                                        .first()
//                                ).apply {
//                                    putValueArgument(0, expression.valueArguments.first().second)
//                                }

                            // TODO this needs to be called with the right receiver, like:
                            //    CALL 'public final fun testFun (): kotlin.Unit declared in <root>.Test' type=kotlin.Unit origin=null
                            //        $this: GET_VAR 'val test: <root>.Test [val] declared in <root>.main' type=<root>.Test origin=null

                            DeclarationIrBuilder(pluginContext, expression.symbol)
                                .irCall(
                                    pluginContext
                                        .referenceFunctions(FqName("_suspendProp_set${propName.capitalize()}"))
                                        .first()
                                ).apply {

                                    putValueArgument(0, expression.valueArguments.first().second)
                                }
                        } else {

                            DeclarationIrBuilder(pluginContext, expression.symbol)
                                .irCall(
                                    pluginContext
                                        .referenceFunctions(FqName("_suspendProp_set${propName.capitalize()}"))
                                        .first()
                                ).apply {
                                    putValueArgument(0, expression.valueArguments.first().second)
                                }
                        }
                    } else expression
                }
                else -> expression
            }

            expression.transformChildrenVoid(this)
            return result
        }
    })
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
