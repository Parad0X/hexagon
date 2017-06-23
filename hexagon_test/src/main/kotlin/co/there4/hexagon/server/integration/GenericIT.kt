package co.there4.hexagon.server.integration

import co.there4.hexagon.client.Client
import co.there4.hexagon.server.*
import co.there4.hexagon.server.HttpMethod.GET
import co.there4.hexagon.templates.pebble.PebbleEngine
import java.time.LocalDateTime
import java.util.Locale.getDefault as defaultLocale

@Suppress("unused") // Test methods are flagged as unused
class GenericIT : ItModule() {
    internal class CustomException : IllegalArgumentException()

    internal data class Tag(
        val id: String = System.currentTimeMillis().toString(),
        val name: String
    )

    private val part = "param"

    private val FORTUNE_MESSAGES = setOf(
        "fortune: No such file or directory",
        "A computer scientist is someone who fixes things that aren't broken.",
        "After enough decimal places, nobody gives a damn.",
        "A bad random number generator: 1, 1, 1, 1, 1, 4.33e+67, 1, 1, 1",
        "A computer program does what you tell it to do, not what you want it to do.",
        "Emacs is a nice operating system, but I prefer UNIX. — Tom Christaensen",
        "Any program that runs right is obsolete.",
        "A list is only as strong as its weakest link. — Donald Knuth",
        "Feature: A bug with seniority.",
        "Computers make very fast, very accurate mistakes.",
        "<script>alert(\"This should not be displayed in a browser alert box.\");</script>",
        "フレームワークのベンチマーク"
    )

    override fun initialize(router: Router) {
        router.before("/protected/*") { halt(401, "Go Away!") }

        router.assets("public")

        router.get("/request/data") {
            response.body = request.url

            request.cookies["method"]?.value = request.method.toString()
            request.cookies["host"]?.value = request.ip
            request.cookies["uri"]?.value = request.url
            request.cookies["params"]?.value = request.parameters.size.toString()

            response.addHeader("agent", request.userAgent)
            response.addHeader("scheme", request.scheme)
            response.addHeader("host", request.host)
            response.addHeader("query", request.queryString)
            response.addHeader("port", request.port.toString())

            ok ("${response.body}!!!")
        }

        router.error(UnsupportedOperationException::class) {
            response.addHeader("error", it.message ?: it.javaClass.name)
        }

        router.error(IllegalArgumentException::class) {
            response.addHeader("runtimeError", it.message ?: it.javaClass.name)
        }

        router.get("/*") { pass() }
        router.get("/exception") { throw UnsupportedOperationException("error message") }
        router.get("/baseException") { throw CustomException() }
        router.get("/unhandledException") { error("error message") }
        router.get("/hi") { ok ("Hello World!") }
        router.get("/param/{param}") { ok ("echo: ${request ["param"]}") }
        router.get("/paramwithmaj/{paramWithMaj}") { ok ("echo: ${request ["paramWithMaj"]}") }
        router.get("/") { ok("Hello Root!") }
        router.post("/poster") { created("Body was: ${request.body}") }
        router.patch("/patcher") { ok ("Body was: ${request.body}") }
        router.delete ("/method") { okRequestMethod () }
        router.options ("/method") { okRequestMethod () }
        router.get ("/method") { okRequestMethod () }
        router.patch ("/method") { okRequestMethod () }
        router.post ("/method") { okRequestMethod () }
        router.put ("/method") { okRequestMethod () }
        router.trace ("/method") { okRequestMethod () }
        router.head ("/method") { response.addHeader ("header", request.method.toString()) }
        router.get("/halt") { halt("halted") }
        router.get("/tworoutes/$part/{param}") { ok ("$part route: ${request ["param"]}") }
        router.get("/template") {
            val locale = defaultLocale()
            val now = LocalDateTime.now()
            template(PebbleEngine, "pebble_template.html", locale, mapOf("date" to now))
        }

        router.get("/tworoutes/${part.toUpperCase()}/{param}") {
            ok ("${part.toUpperCase()} route: ${request ["param"]}")
        }

        router.get("/reqres") { ok (request.method) }

        router.get("/redirect") { redirect("http://example.com") }

        router.after("/hi") {
            response.addHeader ("after", "foobar")
        }
        router.apply {
            GET at "/return/status" by { 201 }
            GET at "/return/body" by { "body" }
            GET at "/return/pair" by { 202 to "funky status" }
            GET at "/return/list" by { listOf("alpha", "beta") }
            GET at "/return/map" by { mapOf("alpha" to 0, "beta" to true) }
            GET at "/return/object" by { Tag(name = "Message") }
            GET at "/return/pair/list" by { 201 to listOf("alpha", "beta") }
            GET at "/return/pair/map" by { 201 to mapOf("alpha" to 0, "beta" to true) }
            GET at "/return/pair/object" by { 201 to Tag(name = "Message") }
        }
    }

    private fun Call.okRequestMethod() = ok (request.method)

    fun reqres(client: Client) {
        val response = client.get("/reqres")
        assertResponseEquals(response, "GET")
    }

    fun getHi(client: Client) {
        val response = client.get("/hi")
        assertResponseEquals(response, "Hello World!")
    }

    fun hiHead(client: Client) {
        val response = client.head("/hi")
        assertResponseEquals(response, "")
    }

    fun template(client: Client) {
        val response = client.get("/template")
        assert(response.statusCode == 200)
    }

    fun getHiAfterFilter(client: Client) {
        val response = client.get ("/hi")
        assertResponseEquals(response, "Hello World!")
        assert(response.headers["after"]?.contains("foobar") ?: false)
    }

    fun getRoot(client: Client) {
        val response = client.get ("/")
        assertResponseEquals(response, "Hello Root!")
    }

    fun echoParam1(client: Client) {
        val response = client.get ("/param/shizzy")
        assertResponseEquals(response, "echo: shizzy")
    }

    fun echoParam2(client: Client) {
        val response = client.get ("/param/gunit")
        assertResponseEquals(response, "echo: gunit")
    }

    fun echoParamWithUpperCaseInValue(client: Client) {
        val camelCased = "ThisIsAValueAndBlacksheepShouldRetainItsUpperCasedCharacters"
        val response = client.get ("/param/" + camelCased)
        assertResponseEquals(response, "echo: $camelCased")
    }

    fun twoRoutesWithDifferentCase(client: Client) {
        var expected = "expected"
        val response1 = client.get ("/tworoutes/$part/$expected")
        assertResponseEquals(response1, "$part route: $expected")

        expected = expected.toUpperCase()
        val response = client.get ("/tworoutes/${part.toUpperCase()}/$expected")
        assertResponseEquals(response, "${part.toUpperCase()} route: $expected")
    }

    fun echoParamWithMaj(client: Client) {
        val response = client.get ("/paramwithmaj/plop")
        assertResponseEquals(response, "echo: plop")
    }

    fun unauthorized(client: Client) {
        val response = client.get ("/protected/resource")
        assert(response.statusCode == 401)
    }

    fun notFound(client: Client) {
        val response = client.get ("/no/resource")
        assertResponseContains(response, 404, "http://localhost:", "/no/resource not found")
    }

    fun postOk(client: Client) {
        val response = client.post ("/poster", "Fo shizzy")
        assertResponseContains(response, 201, "Fo shizzy")
    }

    fun patchOk(client: Client) {
        val response = client.patch ("/patcher", "Fo shizzy")
        assertResponseContains(response, "Fo shizzy")
    }

    fun staticFolder(client: Client) {
        val response = client.get ("/file.txt/")
        assertResponseContains(response, 404, "/file.txt/ not found")
    }

    fun staticFile(client: Client) {
        val response = client.get ("/file.txt")
        assertResponseEquals(response, "file content\n")
    }

    fun fileContentType(client: Client) {
        val response = client.get ("/file.css")
        assert(response.contentType.contains("css"))
        assertResponseEquals(response, "/* css */\n")
    }

    fun halt(client: Client) {
        val response = client.get ("/halt")
        assertResponseEquals(response, "halted", 500)
    }

    fun redirect(client: Client) {
        val response = client.get ("/redirect")
        assert(response.statusCode == 302)
        assert(response.headers["Location"] == "http://example.com")
    }

    // TODO Check with asserts
    fun requestData(client: Client) {
        val response = client.get ("/request/data?query")
        val port = client.endpointUrl.port.toString ()
        val protocol = "http"

//        val protocol = if (testScenario.secure) "https" else "http"
//
//        assert ("error message" == response.cookies["method"].value)
//        assert ("error message" == response.cookies["host"].value)
//        assert ("error message" == response.cookies["uri"].value)
//        assert ("error message" == response.cookies["params"].value)

        assert("AHC/2.0" == response.headers["agent"])
        assert(protocol == response.headers["scheme"])
        assert("127.0.0.1" == response.headers["host"])
        assert("query" == response.headers["query"])
        assert(port == response.headers["port"])

        assert(response.responseBody == "$protocol://localhost:$port/request/data!!!")
        assert(200 == response.statusCode)
    }

    fun handleException(client: Client) {
        val response = client.get ("/exception")
        assert("error message" == response.headers["error"]?.toString())
    }

    fun base_error_handler(client: Client) {
        val response = client.get ("/baseException")
        assert(response.headers["runtimeError"]?.toString() == CustomException::class.java.name)
    }

    fun not_registered_error_handler(client: Client) {
        val response = client.get ("/unhandledException")
        assertResponseContains(response, 500)
    }

    fun return_values (client: Client) {
        assertResponseContains(client.get ("/return/status"), 201)
        assertResponseEquals(client.get ("/return/body"), "body")
        assertResponseEquals(client.get ("/return/pair"), "funky status", 202)
        assertResponseContains(client.get ("/return/list"), "alpha", "beta")
        assertResponseContains(client.get ("/return/map"), "alpha", "beta", "0", "true")
        assertResponseContains(client.get ("/return/object"), "id", "name", "Message")
        assertResponseContains(client.get ("/return/pair/list"), 201, "alpha", "beta")
        assertResponseContains(client.get ("/return/pair/map"), 201, "alpha", "beta", "0", "true")
        assertResponseContains(client.get ("/return/pair/object"), 201, "id", "name", "Message")
    }

    fun methods (client: Client) {
        checkMethod (client, "HEAD", "header") // Head does not support body message
        checkMethod (client, "DELETE")
        checkMethod (client, "OPTIONS")
        checkMethod (client, "GET")
        checkMethod (client, "PATCH")
        checkMethod (client, "POST")
        checkMethod (client, "PUT")
        checkMethod (client, "TRACE")
    }

    private fun checkMethod (client: Client, methodName: String, headerName: String? = null) {
        val res = client.send(HttpMethod.valueOf (methodName), "/method")
        assert (
            if (headerName == null) res.responseBody != null
            else res.headers.get(headerName) == methodName
        )
        assert (200 == res.statusCode)
    }

    override fun validate(client: Client) {
        reqres(client)
        getHi(client)
        hiHead(client)
        template(client)
        getHiAfterFilter(client)
        getRoot(client)
        echoParam1(client)
        echoParam2(client)
        echoParamWithUpperCaseInValue(client)
        twoRoutesWithDifferentCase(client)
        echoParamWithMaj(client)
        unauthorized(client)
        notFound(client)
        postOk(client)
        patchOk(client)
        staticFolder(client)
        staticFile(client)
        fileContentType(client)
        halt(client)
        redirect(client)
        requestData(client)
        handleException(client)
        base_error_handler(client)
        not_registered_error_handler(client)
        return_values (client)
        methods(client)
    }
}

