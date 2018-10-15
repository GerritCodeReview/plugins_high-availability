load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "wiremock",
        artifact = "com.github.tomakehurst:wiremock-standalone:2.19.0",
        sha1 = "1c1439bc822faadd8a7e406325380a4dd5b5ed51",
    )

    maven_jar(
        name = "mockito",
        artifact = "org.mockito:mockito-core:2.23.0",
        sha1 = "497ddb32fd5d01f9dbe99a2ec790aeb931dff1b1",
        deps = [
            "@byte-buddy//jar",
            "@byte-buddy-agent//jar",
            "@objenesis//jar",
        ],
    )

    BYTE_BUDDY_VERSION = "1.9.0"

    maven_jar(
        name = "byte-buddy",
        artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
        sha1 = "8cb0d5baae526c9df46ae17693bbba302640538b",
    )

    maven_jar(
        name = "byte-buddy-agent",
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
        artifact = "org.jgroups:jgroups:4.0.15.Final",
        sha1 = "0179a96ee12112145058d9752aa667f30ab80581",
    )
