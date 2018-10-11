load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "wiremock",
        artifact = "com.github.tomakehurst:wiremock-standalone:2.18.0",
        sha1 = "cf7776dc7a0176d4f4a990155d819279078859f9",
    )

    maven_jar(
        name = "mockito",
        artifact = "org.mockito:mockito-core:2.23.0",
        sha1 = "497ddb32fd5d01f9dbe99a2ec790aeb931dff1b1",
        deps = [
            "@byte_buddy//jar",
            "@byte_buddy_agent//jar",
            "@objenesis//jar",
        ],
    )

    BYTE_BUDDY_VERSION = "1.9.0"

    maven_jar(
        name = "byte_buddy",
        artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
        sha1 = "8cb0d5baae526c9df46ae17693bbba302640538b",
    )

    maven_jar(
        name = "byte_buddy_agent",
        artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
        sha1 = "37b5703b4a6290be3fffc63ae9c6bcaaee0ff856",
    )

    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:2.6",
        sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
    )

    maven_jar(
        name = "jgroups",
        artifact = "org.jgroups:jgroups:3.6.15.Final",
        sha1 = "755afcfc6c8a8ea1e15ef0073417c0b6e8c6d6e4",
    )
