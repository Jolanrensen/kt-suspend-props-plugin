package dev.junron.pyslice


operator fun String.get(_start: Int?, _end: Int?, step: Int = 1): String {
    var start = _start ?: 0
    var end = (_end ?: length) - 1
    if (start < 0) start += length
    if (end < 0) end += length
    if (start == end + 1) return get(start).toString()
    if (start < end && step == 1) {
        return substring(start..end)
    }
    val range = if (step > 0) start..end step step else end downTo start step -step
    return substring(this, range)
}

operator fun <T> Iterable<T>.unaryMinus() = reversed()
operator fun  CharSequence.unaryMinus() = reversed()

private fun substring(string: String, range: IntProgression): String {
    val sb = StringBuilder()
    range.forEach { index ->
        sb.append(string[index])
    }
    return sb.toString()
}
