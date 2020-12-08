import dev.junron.pyslice.NegativeIndex
import dev.junron.pyslice.PythonSlice
import dev.junron.pyslice.get

@NegativeIndex
@PythonSlice
fun main() {
    val string = "Hello, world!"
    // Extension operator function without compiler plugin
    println(string[null, null, -1])
    println(string[null, 7] + "Kotlin!")
    // With compiler plugin
    println(string[::-1])
    println(string[:7]+"Kotlin!")
    // Negative indexes
    println(string[:7]+"Kotlin" + string[-1])
    // More examples
    // Characters with even index
    println(string[::2])
    // Identity
    println(string[::])
    // Replace
    val index = 5
    println(string[:index]+"!"+string[index+1:])
}
