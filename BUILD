load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
    "gerrit_plugin_dependency_tests",
)
load("@rules_java//java:defs.bzl", "java_library")

gerrit_plugin(
    name = "high-availability",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: high-availability",
        "Gerrit-Module: com.ericsson.gerrit.plugins.highavailability.Module",
        "Gerrit-HttpModule: com.ericsson.gerrit.plugins.highavailability.HttpModule",
        "Gerrit-InitStep: com.ericsson.gerrit.plugins.highavailability.Setup",
        "Gerrit-ReloadMode: restart",
        "Implementation-Title: high-availability plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/high-availability",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":global-refdb-neverlink",
        "@high-availability_plugin_deps//:dev_failsafe_failsafe",
        "@high-availability_plugin_deps//:org_jgroups_jgroups",
        "@high-availability_plugin_deps//:org_jgroups_kubernetes_jgroups_kubernetes",
    ],
)

java_library(
    name = "global-refdb-neverlink",
    neverlink = 1,
    exports = ["//plugins/global-refdb"],
)

gerrit_plugin_tests(
    name = "high-availability_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    javacopts = ["-Xep:DoNotMock:OFF"],
    resources = glob(["src/test/resources/**/*"]),
    tags = [
        "high-availability",
        "local",
    ],
    deps = [
        ":high-availability__plugin_test_deps",
    ],
)

java_library(
    name = "high-availability__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = [
        ":high-availability__plugin",
        "//plugins/global-refdb",
        "@high-availability_plugin_deps//:com_github_tomakehurst_wiremock_standalone",
        "@high-availability_plugin_deps//:dev_failsafe_failsafe",
        "@high-availability_plugin_deps//:org_jgroups_jgroups",
    ],
)

gerrit_plugin_dependency_tests(plugin = "high-availability")
