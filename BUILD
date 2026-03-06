load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:in_gerrit_tree.bzl",
    "in_gerrit_tree_enabled",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:runtime_jars_allowlist.bzl",
    "runtime_jars_allowlist_test",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:runtime_jars_overlap.bzl",
    "runtime_jars_overlap_test",
)
load("@rules_java//java:defs.bzl", "java_library")

gerrit_plugin(
    name = "high-availability",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: high-availability",
        "Gerrit-Module: com.ericsson.gerrit.plugins.highavailability.Module",
        "Gerrit-ApiModule: com.ericsson.gerrit.plugins.highavailability.ApiModule",
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

TEST_SRCS = [
    "src/test/java/**/*Test.java",
    "src/test/java/**/*IT.java",
]

java_library(
    name = "testutils",
    testonly = 1,
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = TEST_SRCS,
    ),
    deps = [
        ":high-availability__plugin_test_deps",
    ],
)

gerrit_plugin_tests(
    name = "high-availability_tests",
    srcs = glob(TEST_SRCS),
    javacopts = ["-Xep:DoNotMock:OFF"],
    resources = glob(["src/test/resources/**/*"]),
    tags = [
        "high-availability",
        "local",
    ],
    deps = [
        ":testutils",
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

runtime_jars_allowlist_test(
    name = "check_high-availability_third_party_runtime_jars",
    allowlist = ":high-availability_third_party_runtime_jars.allowlist.txt",
    hint = "plugins/high-availability:check_high-availability_third_party_runtime_jars_manifest",
    target = ":high-availability__plugin",
)

runtime_jars_overlap_test(
    name = "high-availability_no_overlap_with_gerrit",
    against = "//:headless.war.jars.txt",
    hint = "Exclude overlaps via maven.install(excluded_artifacts=[...]) and re-run this test.",
    target = ":high-availability__plugin",
    target_compatible_with = in_gerrit_tree_enabled(),
)
