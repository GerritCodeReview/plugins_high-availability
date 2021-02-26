load("//tools/bzl:maven_jar.bzl", "maven_jar")
load("@bazel_tools//tools/build_defs/repo:java.bzl", "java_import_external")

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

    java_import_external(
        name = "global-refdb",
        jar_sha256 = "480a76b983854a168e4da8e1f15454992a7b83d9a6afcc41c85231abb184cb72",
        jar_urls = [
            "https://repo1.maven.org/maven2/com/gerritforge/global-refdb/3.3.2.1/global-refdb-3.3.2.1-jdk8.jar",
        ],
        licenses = ["notice"],  # Apache 2.0
    )

    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.3.0-rc1",
        sha1 = "1b005b31c27a30ff10de97f903fa2834051bcadf",
    )
