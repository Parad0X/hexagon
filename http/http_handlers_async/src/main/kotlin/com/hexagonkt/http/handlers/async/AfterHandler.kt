package com.hexagonkt.http.handlers.async

import com.hexagonkt.handlers.async.AfterHandler
import com.hexagonkt.handlers.async.Handler
import com.hexagonkt.http.model.HttpMethod
import com.hexagonkt.http.model.HttpStatus
import com.hexagonkt.http.model.HttpCall
import kotlin.reflect.KClass

data class AfterHandler(
    override val handlerPredicate: HttpPredicate = HttpPredicate(),
    val block: HttpCallback
) : HttpHandler, Handler<HttpCall> by AfterHandler(handlerPredicate, toCallback(block)) {

    constructor(
        methods: Set<HttpMethod> = emptySet(),
        pattern: String = "",
        exception: KClass<out Exception>? = null,
        status: HttpStatus? = null,
        block: HttpCallback,
    ) :
        this(HttpPredicate(methods, pattern, exception, status), block)

    constructor(method: HttpMethod, pattern: String = "", block: HttpCallback) :
        this(setOf(method), pattern, block = block)

    constructor(pattern: String, block: HttpCallback) :
        this(emptySet(), pattern, block = block)

    override fun addPrefix(prefix: String): HttpHandler =
        copy(handlerPredicate = handlerPredicate.addPrefix(prefix))
}