load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "wiremock",
        artifact = "com.github.tomakehurst:wiremock-standalone:2.27.2",
        sha1 = "327647a19b2319af2526b9c33a5733a2241723e0",
    )

    maven_jar(
        name = "jgroups",
        artifact = "org.jgroups:jgroups:3.6.15.Final",
        sha1 = "755afcfc6c8a8ea1e15ef0073417c0b6e8c6d6e4",
    )

    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.5.4",
        sha1 = "6f96965d4cedd8b01b1fd9047d8c443c752bd675",
    )
