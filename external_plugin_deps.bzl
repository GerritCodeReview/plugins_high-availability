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

    TESTCONTAINERS_VERSION = "1.21.3"

    maven_jar(
        name = "testcontainers",
        artifact = "org.testcontainers:testcontainers:" + TESTCONTAINERS_VERSION,
        sha1 = "aa3e792d2cf4598019933c42f1cfa55bd608ce8b",
    )

    maven_jar(
        name = "testcontainers-gcloud",
        artifact = "org.testcontainers:gcloud:" + TESTCONTAINERS_VERSION,
        sha1 = "5134c3849d2acb6979ac8e51e3a4ae52d799673a",
    )

    maven_jar(
        name = "duct-tape",
        artifact = "org.rnorth.duct-tape:duct-tape:1.0.8",
        sha1 = "92edc22a9ab2f3e17c9bf700aaee377d50e8b530",
    )

    maven_jar(
        name = "visible-assertions",
        artifact = "org.rnorth.visible-assertions:visible-assertions:2.1.2",
        sha1 = "20d31a578030ec8e941888537267d3123c2ad1c1",
    )

    maven_jar(
        name = "jna",
        artifact = "net.java.dev.jna:jna:5.5.0",
        sha1 = "0e0845217c4907822403912ad6828d8e0b256208",
    )

    DOCKER_JAVA_VERS = "3.6.0"

    maven_jar(
        name = "docker-java-api",
        artifact = "com.github.docker-java:docker-java-api:" + DOCKER_JAVA_VERS,
        sha1 = "caeb5bee6a9c07bff31f73ace576436168e2aa47",
    )

    maven_jar(
        name = "docker-java-transport",
        artifact = "com.github.docker-java:docker-java-transport:" + DOCKER_JAVA_VERS,
        sha1 = "d522c467aad17fd927e0db0130d2849a321a36aa",
    )

    maven_jar(
        name = "docker-java-transport-zerodep",
        artifact = "com.github.docker-java:docker-java-transport-zerodep:" + DOCKER_JAVA_VERS,
        sha1 = "549f4985f9c7714deff47d1041603e85e132d184",
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

    maven_jar(
        name = "aws_sdk_sns",
        artifact = "software.amazon.awssdk:sns:2.29.15",
        sha1 = "3271de1c86221da6ab2859dc21a7a5aad088456e",
    )

    maven_jar(
        name = "aws_sdk_sqs",
        artifact = "software.amazon.awssdk:sqs:2.29.15",
        sha1 = "d169f0e0645a4281d34758828b85762b52d643b8",
    )

    maven_jar(
        name = "aws_sdk_netty",
        artifact = "software.amazon.awssdk:netty-nio-client:2.29.15",
        sha1 = "2f17728a99424a63c1cfc8e37e3c4ecd48281872",
    )

    maven_jar(
        name = "aws_auth",
        artifact = "software.amazon.awssdk:auth:2.29.15",
        sha1 = "a158e23aa752f7a240ba08def255bcc81465fec3",
    )

    maven_jar(
        name = "aws_regions",
        artifact = "software.amazon.awssdk:regions:2.29.15",
        sha1 = "2a07489790581760abdadaffa36027ce7619e7b9",
    )

    maven_jar(
        name = "aws_identity_spi",
        artifact = "software.amazon.awssdk:identity-spi:2.29.15",
        sha1 = "892d5b9476bdfcf0f7f177b45995d6cf17e5da2b",
    )

    maven_jar(
        name = "aws_utils",
        artifact = "software.amazon.awssdk:utils:2.29.15",
        sha1 = "9f4c25b1aef2c80eeba6c6a8e4b0eed6453224aa",
    )

    maven_jar(
        name = "aws_http_client_spi",
        artifact = "software.amazon.awssdk:http-client-spi:2.29.15",
        sha1 = "81f806d3aabf78b687895bdd11c4e9560bc029f2",
    )

    maven_jar(
        name = "aws_sns",
        artifact = "software.amazon.awssdk:sns:2.29.15",
        sha1 = "3271de1c86221da6ab2859dc21a7a5aad088456e",
    )

    maven_jar(
        name = "aws_core",
        artifact = "software.amazon.awssdk:aws-core:2.29.15",
        sha1 = "610fb7f6805c0b8aefd0375ae6ec8a8e25e7a999",
    )

    maven_jar(
        name = "aws_sdk_core",
        artifact = "software.amazon.awssdk:sdk-core:2.29.15",
        sha1 = "1925ab5140147caa628a8e60ebf5cceaafbd3bf8",
    )

    maven_jar(
        name = "aws_retries_spi",
        artifact = "software.amazon.awssdk:retries-spi:2.29.15",
        sha1 = "6140215780464bab2d0682b380962076800d8006",
    )

    maven_jar(
        name = "aws_profiles",
        artifact = "software.amazon.awssdk:profiles:2.29.15",
        sha1 = "b5c9fd6e7e84459f324a39465d12ed4de552bd4d",
    )

    maven_jar(
        name = "aws_endpoints_spi",
        artifact = "software.amazon.awssdk:endpoints-spi:2.29.15",
        sha1 = "4ed1811cf531ab3df0a8f54593360476773226f4",
    )

    maven_jar(
        name = "aws_http_auth_api",
        artifact = "software.amazon.awssdk:http-auth-spi:2.29.15",
        sha1 = "c9d8d747752f4b96eb2406f755be14f0dbe06dc0",
    )

    maven_jar(
        name = "netty-common",
        artifact = "io.netty:netty-common:4.1.115.Final",
        sha1 = "9da10a9f72e3f87e181d91b525174007a6fc4f11",
    )

    maven_jar(
        name = "netty-transport",
        artifact = "io.netty:netty-transport:4.1.115.Final",
        sha1 = "39cef77c1a25908ac1abf4960c2e789f0bf70ff9",
    )

    maven_jar(
        name = "aws_http_auth_aws",
        artifact = "software.amazon.awssdk:http-auth-aws:2.29.15",
        sha1 = "a70592df0038ac7c6110359d70305bc8ba0ead56",
    )

    maven_jar(
        name = "aws_http_auth",
        artifact = "software.amazon.awssdk:http-auth:2.29.15",
        sha1 = "41fb12e1f9d8df0a517d1fbbcbb462a95a2f6e5f",
    )

    maven_jar(
        name = "reactive-streams",
        artifact = "org.reactivestreams:reactive-streams:1.0.4",
        sha1 = "3864a1320d97d7b045f729a326e1e077661f31b7",
    )

    maven_jar(
        name = "aws_retries",
        artifact = "software.amazon.awssdk:retries:2.29.15",
        sha1 = "9ca1397bff09b2a14895de65ece16e0cb17a4b75",
    )

    maven_jar(
        name = "netty-handler",
        artifact = "io.netty:netty-handler:4.1.115.Final",
        sha1 = "d54dbf68b9d88a98240107758c6b63da5e46e23a",
    )

    maven_jar(
        name = "netty-buffer",
        artifact = "io.netty:netty-buffer:4.1.115.Final",
        sha1 = "d5daf1030e5c36d198caf7562da2441a97ec0df6",
    )

    maven_jar(
        name = "aws_metrics_spi",
        artifact = "software.amazon.awssdk:metrics-spi:2.29.15",
        sha1 = "88322ea9e46a644ca9381d92e443ff0bd50a5dc1",
    )

    maven_jar(
        name = "aws_query_protocol",
        artifact = "software.amazon.awssdk:aws-query-protocol:2.29.15",
        sha1 = "d51d6de32e2a9668487480e802539f5c78269d88",
    )

    maven_jar(
        name = "aws_protocol_core",
        artifact = "software.amazon.awssdk:protocol-core:2.29.15",
        sha1 = "419d3d7d727e22a9887dee85357174b4e22c05b2",
    )

    maven_jar(
        name = "aws_checksums",
        artifact = "software.amazon.awssdk:checksums:2.29.15",
        sha1 = "76c9516e0486ec2e7514737cbe3c362ab2f4e7d1",
    )

    maven_jar(
        name = "aws_checksums_spi",
        artifact = "software.amazon.awssdk:checksums-spi:2.29.15",
        sha1 = "59a335f971c7a4c9fe02721452eebc6fee263bb1",
    )

    maven_jar(
        name = "netty-codec-http2",
        artifact = "io.netty:netty-codec-http2:4.1.115.Final",
        sha1 = "0bc474c27c96e3a309da73160fbcfe0bd3aa85bc",
    )

    maven_jar(
        name = "netty-codec-http",
        artifact = "io.netty:netty-codec-http:4.1.115.Final",
        sha1 = "80f0dece29a2c0269217e8dd1b6db6ff9710781f",
    )

    maven_jar(
        name = "netty-codec",
        artifact = "io.netty:netty-codec:4.1.115.Final",
        sha1 = "d326bf3a4c785b272da3db6941779a1bd5448378",
    )

    maven_jar(
        name = "netty-resolver",
        artifact = "io.netty:netty-resolver:4.1.115.Final",
        sha1 = "e33b4d476c03975957f5d8d0319d592bf2bc5e96",
    )

    maven_jar(
        name = "aws_json_protocol",
        artifact = "software.amazon.awssdk:aws-json-protocol:2.29.15",
        sha1 = "77092a930c8c8dff7e1c42a28c044a5c53f9308e",
    )

    maven_jar(
        name = "aws_json_utils",
        artifact = "software.amazon.awssdk:json-utils:2.29.15",
        sha1 = "13de26806b6ad6093ed835dfb7e457d232357b12",
    )

    maven_jar(
        name = "aws_third_party_jackson_core",
        artifact = "software.amazon.awssdk:third-party-jackson-core:2.29.15",
        sha1 = "34f3f8357d790d683866358551b4642be77f4c24",
    )

    maven_jar(
        name = "aws_crt_client",
        artifact = "software.amazon.awssdk:aws-crt-client:2.29.15",
        sha1 = "ba23139f04b9b2e93db4ba37031a42e43bd1ef8b",
    )

    maven_jar(
        name = "aws_crt",
        artifact = "software.amazon.awssdk.crt:aws-crt:0.31.3",
        sha1 = "d2c19088e4fd2a24889a9b3d24b40ba967f64168",
    )
    maven_jar(
        name = "aws_crt_core",
        artifact = "software.amazon.awssdk:crt-core:2.29.15",
        sha1 = "893f6fd172ebff1553926a7edbae89ca241b0175",
    )
    maven_jar(
        name = "netty_transport_native_unix_common",
        artifact = "io.netty:netty-transport-native-unix-common:4.1.115.Final",
        sha1 = "dc96c67d06cd6b5eb677f2728f27bf2e3d9a7284",
    )

