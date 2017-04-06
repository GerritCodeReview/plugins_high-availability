load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
)

gerrit_plugin(
    name = "high-availability",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
    manifest_entries = [
        "Gerrit-PluginName: high-availability",
        "Gerrit-Module: com.ericsson.gerrit.plugins.highavailability.Module",
        "Gerrit-HttpModule: com.ericsson.gerrit.plugins.highavailability.HttpModule",
        "Implementation-Title: high-availability plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/high-availability",
        "Implementation-Vendor: Ericsson",
    ],
)

junit_tests(
    name = "high_availability_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["high-availability"],
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":high-availability__plugin",
        "@mockito//jar",
        "@wiremock//jar",
    ],
)
