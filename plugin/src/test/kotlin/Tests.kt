import arrow.meta.plugin.testing.CompilerTest
import arrow.meta.plugin.testing.assertThis
import nl.jolanrensen.suspendPropertyPlugin.SuspendPropertyPlugin
import org.intellij.lang.annotations.Language
import org.junit.Test

class Tests {

    @Test
    fun `main test`() {

        @Language("kotlin")
        val source = """
            suspend fun main() {
                println(pointless(testProp))
                testProp = 2
                testProp += 2
            }

            fun pointless(something: Any?) = something

            suspend var testProp: Int
                get() {
                    // say what
                    return 3
                }
                set(value) = println(value)
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            suspend fun main() {
                println(pointless(testProp))
                testProp = 2
                testProp += 2
            }

            fun pointless(something: Any?) = something

            annotation class _SuspendProp
            @_SuspendProp
            var testProp: Int
               get() = throw Exception()
               set(value) = throw Exception()

            suspend fun _getTestProp(): Int {
                // say what
                return 3
            }
            suspend fun _setTestProp(value: Int) = println(value)
        """.trimIndent()

        assertThis(CompilerTest(
            config = {

                listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }


    @Test
    fun `simple test`() {

        @Language("kotlin")
        val source = """
            suspend var testProp: Int 
                get() {
                   // say what
                   return 3 
               }
               set(value) = println(value)
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class _SuspendProp
            @_SuspendProp
            var testProp: Int
               get() = throw Exception()
               set(value) = throw Exception()

            suspend fun _getTestProp(): Int {
               // say what
               return 3
            }
            suspend fun _setTestProp(value: Int) = println(value)
        """.trimIndent()

        assertThis(CompilerTest(
            config = {

                listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `simple test with access`() {

        @Language("kotlin")
        val source = """
            suspend fun testFun2(): Int = testProp
            
            suspend fun testFun() {
                val a: Int
                a = testProp
            }
            
            suspend var testProp: Int
               get() {
                   // say what
                   return 3 
               }
               set(value) = println(value)
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            suspend fun testFun2(): Int = testProp
            
            suspend fun testFun() {
                val a: Int
                a = testProp
            }
            
            annotation class _SuspendProp
            @_SuspendProp
            var testProp: Int
               get() = throw Exception()
               set(value) = throw Exception()
            
            suspend fun _getTestProp(): Int {
               // say what
               return 3
            }
            suspend fun _setTestProp(value: Int) = println(value)
        """.trimIndent()

        // after this, in IR testProp is mapped to _getTestProp()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `simple test with visibility`() {

        @Language("kotlin")
        val source = """
            suspend fun test() = testProp

            private suspend var testProp: Int
               get() = 4
               set(value) {
                   println(value)
               }
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            suspend fun test() = testProp

            annotation class _SuspendProp
            @_SuspendProp
            private var testProp: Int
               get() = throw Exception()
               set(value) = throw Exception()
            
            private suspend fun _getTestProp(): Int = 4
            private suspend fun _setTestProp(value: Int) {
               println(value)
            }
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `val test`() {

        @Language("kotlin")
        val source = """
            private suspend val testProp: Int
               get() = 4
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class _SuspendProp
            @_SuspendProp
            private val testProp: Int
               get() = throw Exception()
            
            private suspend fun _getTestProp(): Int = 4
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `calling suspend fun from getter`() {

        @Language("kotlin")
        val source = """
            suspend fun testFun(): Int = 4

            private suspend val testProp: Int
               get() = testFun()
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            suspend fun testFun(): Int = 4
            
            annotation class _SuspendProp
            @_SuspendProp
            private val testProp: Int
               get() = throw Exception()
            
            private suspend fun _getTestProp(): Int = testFun()
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }


    @Test // TODO this probably should fail
    fun `test with initializer`() {

        @Language("kotlin")
        val source = """
            suspend fun testFun(): Int = 4

            private suspend var testProp: Int = testFun()
               get() = field + 1
               set(value) {
                   println(value)
               }
        """.trimIndent()

        // TODO!!
        @Language("kotlin")
        val expectedOutput = """
            suspend fun testFun(): Int = 4
            
            annotation class _SuspendProp
            @_SuspendProp
            private var testProp: Int
               get() = throw Exception()
               set(value) = throw Exception()
            
            private var testProp: Int? = null
            private suspend fun _getTestProp(): Int = testProp + 1
            private suspend fun _setTestProp(value: Int) {
               println(value)
            }
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test // TODO will fail for now
    fun `test with only initializer`() {

        @Language("kotlin")
        val source = """
            private suspend var testProp: Int = 4
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class _SuspendProp
            @_SuspendProp
            private var testProp: Int = 4
            
            private suspend fun _getTestProp(): Int = 4
            private suspend fun _setTestProp(value: Int) {
               println(value)
            }
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }
}