package com.example

import hello.HELLO

fun main() {
    val hello = HELLO()
    println(hello.foo())

    val builder = AClassBuilder()
    builder
        .withA(1)
        .withB("foo")
        .withC(2.3)
    val aClass: AClass = builder.build()
    println(aClass.foo())
}
