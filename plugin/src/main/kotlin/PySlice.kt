package dev.junron.pyslice

import arrow.meta.CliPlugin
import arrow.meta.Meta
import arrow.meta.invoke
import arrow.meta.phases.CompilerContext
import arrow.meta.phases.analysis.body
import arrow.meta.quotes.Transform
import arrow.meta.quotes.namedFunction
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.contracts.ExperimentalContracts

class PySlicePlugin : Meta {
    @ExperimentalContracts
    override fun intercept(ctx: CompilerContext) = listOf(pythonSlice)
}

annotation class PythonSlice
annotation class NegativeIndex

val Meta.pythonSlice: CliPlugin
    get() = "PythonSlice" {
        meta(
            namedFunction(this,
                { hasAnnotation("NegativeIndex") || hasAnnotation("PythonSlice") }) { fn ->
                Transform.replace(
                    replacing = fn,
                    newDeclaration = replace(
                        fn,
                        fn.hasAnnotation("NegativeIndex"),
                        fn.hasAnnotation("PythonSlice")
                    ).function.syntheticScope
                )
            }
        )
    }

private fun replace(
    function: KtNamedFunction,
    negativeIndex: Boolean,
    pythonSlice: Boolean,
): String {
    val functionName = function.name
    val functionBody = function.body()?.text?.let { code ->
        val newCode =
            if (negativeIndex) replaceNegativeIndexCode(code) else code
        if (pythonSlice) replacePythonSliceCode(newCode) else newCode
    }
    val debug = false
    return """
        ${if (debug) "| //metadebug\n" else ""}
        | ${function.visibilityModifier()?.text ?: ""} fun ${functionName}(${
        function.valueParameters.joinToString(",") { it.text }
    })$functionBody"""
}

private fun replaceNegativeIndexCode(code: String): String {
    val regex = Regex("([a-zA-Z0-9]+)\\[-([0-9]+)\\]")
    return regex.replace(code) {
        val (_, variable, index) = it.groupValues
        variable + "[$variable.lastIndex-${index.toInt() - 1}]"
    }
}


private fun replacePythonSliceCode(code: String): String {
    val regex = Regex("([a-zA-Z0-9]+)\\[([^\\[:]*?:[^:\\]]*?(:[^:\\]]*?)?)\\]")
    return regex.replace(code) {
        val (_, variable, slice) = it.groupValues
        val indexes = slice.split(":").map { char -> char.ifEmpty { null } }
        val (a, b) = indexes
        // All specified are null
        if (a == null && b == null && (indexes.size == 2 || indexes[2] == null)) {
            return@replace variable
        }
        variable + "[$a, $b${if (indexes.size > 2) ", ${indexes[2]}" else ""}]"
    }
}

fun KtAnnotated.hasAnnotation(annotation: String): Boolean {
    val predicate: (KtAnnotationEntry) -> Boolean = {
        it.typeReference
            ?.typeElement
            ?.safeAs<KtUserType>()
            ?.referencedName == annotation
    }
    return annotationEntries.any(predicate)
}
