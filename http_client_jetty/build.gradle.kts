
plugins {
    id("java-library")
}

apply(from = "../gradle/kotlin.gradle")
apply(from = "../gradle/publish.gradle")
apply(from = "../gradle/dokka.gradle")

dependencies {
    val jettyVersion = properties["jettyVersion"]

    api(project(":http_client"))
    api("org.eclipse.jetty:jetty-client:$jettyVersion")

    testImplementation("org.eclipse.jetty.websocket:websocket-jetty-client:$jettyVersion")
}