import arrow.meta.plugin.testing.CompilerTest
import arrow.meta.plugin.testing.assertThis
import nl.jolanrensen.suspendPropertyPlugin.SuspendPropertyPlugin
import org.intellij.lang.annotations.Language
import org.junit.Test

class DelegateTests {

    @Test
    fun `simple test 1`() {

        @Language("kotlin")
        val source = """
            import kotlin.reflect.KProperty

            annotation class SuspendProp
            
            suspend fun doesNothing() {}
            
            class Test {
                suspend operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
                    doesNothing()
                    return 5
                }
                suspend operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    doesNothing()
                    println(value)
                }
            }

            suspend var c by Test()
            
            suspend fun main() {
                c = 5
            }
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            import kotlin.reflect.KProperty

            annotation class SuspendProp
            
            suspend fun doesNothing() {}
            
            class Test {
                @SuspendProp
                operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = throw IllegalStateException("This call is replaced with _suspendProp_getValue() at compile time.")
              
                suspend fun _suspendProp_getValue(thisRef: Any?, property: KProperty<*>): Int {
                    doesNothing()
                    return 5
                }
                
                @SuspendProp
                operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) = throw IllegalStateException("This call is replaced with _suspendProp_setValue() at compile time.")
                
                suspend fun _suspendProp_setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    doesNothing()
                    println(value)
                }
            }

            var _suspendProp_c = Test()
            
            @SuspendProp
            var c by _suspendProp_c
            
            suspend fun main() {
                c = 5
            }
        """.trimIndent()

        // in IR replace all calls to @SuspendProp annotated elements

        // TODO [c = 5] becomes [_suspendProp_c._suspendProp_setValue(null, ::c, 5)] in IR

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    // TODO this will not work for now due to lack of local references
    @Test
    fun `local delegate suspend prop`() {

        @Language("kotlin")
        val source = """
            import kotlinx.coroutines.delay
            import kotlin.reflect.KProperty

            annotation class SuspendProp
            
            class Test {
                suspend operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
                    delay(2000)
                    return 5
                }
                suspend operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    println(value)
                }
            }
            
            suspend fun b() {
                var a by Test()
                a = 5
            }
            
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            import kotlinx.coroutines.delay
            import kotlin.reflect.KProperty

            annotation class SuspendProp
            
            class Test {
                @SuspendProp
                operator fun getValue(thisRef: Any?, property: KProperty<*>): Int =
                    throw IllegalStateException()
            
                suspend fun _suspendProp_getValue(thisRef: Any?, property: KProperty<*>): Int {
                    delay(2000)
                    return 5
                }
            
                @SuspendProp
                operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int): Unit =
                    throw IllegalStateException()
            
                suspend fun _suspendProp_setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    println(value)
                }
            }
            
            suspend fun b() {
                val _suspendProp_a = Test()
                var a by _suspendProp_a
                a = 5
            }
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }
}