load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "high-availability",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: high-availability",
        "Gerrit-Module: com.ericsson.gerrit.plugins.highavailability.Module",
        "Gerrit-HttpModule: com.ericsson.gerrit.plugins.highavailability.HttpModule",
        "Gerrit-InitStep: com.ericsson.gerrit.plugins.highavailability.Setup",
        "Implementation-Title: high-availability plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/high-availability",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":gcp-client",
        ":global-refdb-neverlink",
        "@failsafe//jar",
        "@jgroups-kubernetes//jar",
        "@jgroups//jar",
    ],
)

GCP_PUBSUB_CLIENT_LIBS = [
    "@api-common//jar",
    "@gax-grpc//jar",
    "@gax//jar",
    "@google-auth-library-credentials//jar",
    "@google-auth-library-oauth2-http//jar",
    "@google-cloud-pubsub-proto//jar",
    "@google-cloud-pubsub//jar",
    "@google-http-client-gson//jar",
    "@google-http-client//jar",
    "@grpc-alts//jar",
    "@grpc-api//jar",
    "@grpc-auth//jar",
    "@grpc-context//jar",
    "@grpc-core//jar",
    "@grpc-netty-shaded//jar",
    "@grpc-protobuf-lite//jar",
    "@grpc-protobuf//jar",
    "@grpc-stub//jar",
    "@opencensus-api//jar",
    "@opencensus-contrib-http-util//jar",
    "@opentelemetry//jar",
    "@perfmark-api//jar",
    "@proto-google-common-protos//jar",
    "@proto-google-iam-v1//jar",
    "@threetenbp//jar",
]

java_library(
    name = "gcp-client",
    exports = GCP_PUBSUB_CLIENT_LIBS,
)

java_library(
    name = "global-refdb-neverlink",
    neverlink = 1,
    exports = ["//plugins/global-refdb"],
)

junit_tests(
    name = "high-availability_tests",
    srcs = glob(["src/test/java/**/*Test.java", "src/test/java/**/*IT.java"]),
    javacopts = ["-Xep:DoNotMock:OFF"],
    resources = glob(["src/test/resources/**/*"]),
    tags = [
        "high-availability",
        "local",
    ],
    deps = [
        ":high-availability_test_util",
        ":high-availability__plugin_test_deps",
    ],
)

java_library(
    name = "high-availability_test_util",
    testonly = True,
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = ["src/test/java/**/*Test.java"],
    ),
    deps = [
        ":high-availability__plugin_test_deps",
    ]
)

java_library(
    name = "high-availability__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":high-availability__plugin",
        ":gcp-client",
        "@global-refdb//jar",
        "@wiremock//jar",
        "@jgroups//jar",
        "@commons-net//jar",
        "@failsafe//jar",
        "@duct-tape//jar",
        "@jackson-annotations//jar",
    ],
)
