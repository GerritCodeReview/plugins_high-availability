load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_TEST_DEPS")
load("//tools/bzl:classpath.bzl", "classpath_collector")
load("@com_googlesource_gerrit_bazlets//tools:py_binary_path.bzl", "py_binary_path")

java_library(
    name = "classpath",
    runtime_deps = [
        "//:high-availability__plugin_test_deps"
    ],
)

classpath_collector(
    name = "main_classpath_collect",
    #testonly = 1,
    deps = [
        "//:high-availability__plugin_test_deps"
    ],
)

py_binary_path(
    name = "project.py",
    py_binary_label = "@com_googlesource_gerrit_bazlets//tools/eclipse:project",
)
