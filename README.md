# PySlice-kt

A Kotlin compiler plugin/extension operator function implementation of Python collection slicing

### Warning: Not for production use
This was intended as more of an experiment than an actual project.

## Usage
See [plugin-demo](./plugin-demo/src/main/kotlin/Main.kt) for full examples
```kotlin
import dev.junron.pyslice.NegativeIndex
import dev.junron.pyslice.PythonSlice
import dev.junron.pyslice.get

@NegativeIndex
@PythonSlice
fun main() {
    val string = "Hello, world!"
    
    println(string[:: - 1])
    // -> !dlrow ,olleH
    println(string[:7]+"Kotlin!")
    // -> Hello, Kotlin!
    
    // Negative indexes
    println(string[:7]+"Kotlin"+string[-1])
    // -> Hello, Kotlin!
}
```

## Known bugs/future improvements
1. Import `import dev.junron.pyslice.get` is not automatically inserted by the plugin
2. Add support for collections other than strings
3. The regex is probably really sketchy
4. Find out how to package and distribute this
5. Write Intellij plugin


## References
1. [Arrow Meta](https://meta.arrow-kt.io/)
2. [Arrow Meta examples](https://github.com/arrow-kt/arrow-meta-examples/tree/master/hello-world)

