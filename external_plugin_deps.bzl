load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "wiremock",
        artifact = "com.github.tomakehurst:wiremock-standalone:2.27.2",
        sha1 = "327647a19b2319af2526b9c33a5733a2241723e0",
    )

    maven_jar(
        name = "jgroups",
        artifact = "org.jgroups:jgroups:5.2.16.Final",
        sha1 = "d2dceef4c6917239350f2a604b4116745a1e84ae",
    )

    maven_jar(
        name = "jgroups-kubernetes",
        artifact = "org.jgroups.kubernetes:jgroups-kubernetes:2.0.1.Final",
        sha1 = "4e259af98c3b1fbdc8ebaebe42496ef560dfc30f",
    )

    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.7.4",
        sha1 = "a5f3fcdbc04b7e98c52ecd50d2a56424e60b0575",
    )

    maven_jar(
        name = "failsafe",
        artifact = "dev.failsafe:failsafe:3.3.2",
        sha1 = "738a986f1f0e4b6c6a49d351dddc772d1378c5a8",
    )
