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

    maven_jar(
        name = "google-cloud-pubsub",
        artifact = "com.google.cloud:google-cloud-pubsub:1.141.2",
        sha1 = "6d8c4023cf6091c79cf2e4725f9591c99d21559e",
    )

    maven_jar(
        name = "google-cloud-pubsub-proto",
        artifact = "com.google.api.grpc:proto-google-cloud-pubsub-v1:1.123.2",
        sha1 = "2ab1e3839c0b678f3e9cb6e63bf9b97cedb4864d",
    )

    maven_jar(
        name = "api-common",
        artifact = "com.google.api:api-common:2.53.0",
        sha1 = "96e6ff53548b176c5d046049dc8fb1c64546a96a",
    )

    maven_jar(
        name = "google-auth-library-credentials",
        artifact = "com.google.auth:google-auth-library-credentials:1.37.1",
        sha1 = "894c1cd371380e254290ac7c7df04372bf547a8f",
    )

    maven_jar(
        name = "google-auth-library-oauth2-http",
        artifact = "com.google.auth:google-auth-library-oauth2-http:1.37.1",
        sha1 = "86a3c90a6b80128fccac09dead6158fe7cc5e7bd",
    )

    maven_jar(
        name = "gax",
        artifact = "com.google.api:gax:2.70.0",
        sha1 = "6fb01d2c856a6f5a46b8390efa160891c25041f8",
    )

    maven_jar(
        name = "jackson-annotations",
        artifact = "com.fasterxml.jackson.core:jackson-annotations:2.19.2",
        sha1 = "0c5381f11988ae3d424b197a26087d86067b6d7d",
    )

    maven_jar(
        name = "grpc-api",
        artifact = "io.grpc:grpc-api:1.74.0",
        sha1 = "724c36dbd3481ad1755ea7e5f294bd07f694e8b2",
    )

    maven_jar(
        name = "gax-grpc",
        artifact = "com.google.api:gax-grpc:2.70.0",
        sha1 = "5a37a625c4750ca70256d3dbdb6bb7cb13e5b452",
    )

    maven_jar(
        name = "grpc-core",
        artifact = "io.grpc:grpc-core:1.74.0",
        sha1 = "2246702e286fa23369a253391102592d6f2687cc",
    )

    maven_jar(
        name = "grpc-netty-shaded",
        artifact = "io.grpc:grpc-netty-shaded:1.74.0",
        sha1 = "8f98d2f4fda6f09e34d31642716ee4b8a9a22266",
    )

    maven_jar(
        name = "threetenbp",
        artifact = "org.threeten:threetenbp:1.7.2",
        sha1 = "a7db9e22c8b2b1fe3d8f1be9601b13d20d0ba99c",
    )

    maven_jar(
        name = "grpc-alts",
        artifact = "io.grpc:grpc-alts:1.74.0",
        sha1 = "27731ac01ad724d49ca2a7119b3aace2af1af9d5",
    )

    maven_jar(
        name = "grpc-protobuf",
        artifact = "io.grpc:grpc-protobuf:1.74.0",
        sha1 = "67383a4aef66a5b142f766004fdaf95c4fb35f08",
    )

    maven_jar(
        name = "grpc-protobuf-lite",
        artifact = "io.grpc:grpc-protobuf-lite:1.74.0",
        sha1 = "4df4d946ff21aaaa847db7c1c7e44ed1bc5d7dfa",
    )

    maven_jar(
        name = "proto-google-iam-v1",
        artifact = "com.google.api.grpc:proto-google-iam-v1:1.56.0",
        sha1 = "6040188a5df95b50cea137927868416ee2520b64",
    )

    maven_jar(
        name = "proto-google-common-protos",
        artifact = "com.google.api.grpc:proto-google-common-protos:2.61.0",
        sha1 = "4e9550b97769810f105cfee4a6ab8e63873e3c21",
    )

    maven_jar(
        name = "google-http-client",
        artifact = "com.google.http-client:google-http-client:2.0.0",
        sha1 = "bcb2279b29d541ab39439b244feeef2bb9feacd8",
    )

    maven_jar(
        name = "google-http-client-gson",
        artifact = "com.google.http-client:google-http-client-gson:2.0.0",
        sha1 = "59b964f378897f070d1eb3052a7207583d190536",
    )

    maven_jar(
        name = "grpc-context",
        artifact = "io.grpc:grpc-context:1.74.0",
        sha1 = "1bed271c8df99d0e2f7e3e98194edf3e376060a8",
    )

    maven_jar(
        name = "grpc-stub",
        artifact = "io.grpc:grpc-stub:1.74.0",
        sha1 = "a71984325b35c67ac13dd373b0cc6e575e10c1c8",
    )

    maven_jar(
        name = "perfmark-api",
        artifact = "io.perfmark:perfmark-api:0.27.0",
        sha1 = "f86f575a41b091786a4b027cd9c0c1d2e3fc1c01",
    )

    maven_jar(
        name = "opencensus-api",
        artifact = "io.opencensus:opencensus-api:0.31.1",
        sha1 = "66a60c7201c2b8b20ce495f0295b32bb0ccbbc57",
    )

    maven_jar(
        name = "opencensus-contrib-http-util",
        artifact = "io.opencensus:opencensus-contrib-http-util:0.31.1",
        sha1 = "3c13fc5715231fadb16a9b74a44d9d59c460cfa8",
    )

    maven_jar(
        name = "grpc-auth",
        artifact = "io.grpc:grpc-auth:1.74.0",
        sha1 = "3eb142b23a57068a807b821d2c3c979513a928e5",
    )

    maven_jar(
        name = "opentelemetry",
        artifact = "io.opentelemetry:opentelemetry-context:1.47.0",
        sha1 = "86e49fe98ce06c279f7b9f028af8658cb7bc972a",
    )
