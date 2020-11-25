Build
=====

This plugin can be built with Bazel in the Gerrit tree.

Clone or link this plugin to the plugins directory of Gerrit's
source tree. Put the external dependency Bazel build file into
the Gerrit /plugins directory, replacing the existing empty one.

```
  cd gerrit/plugins
  rm external_plugin_deps.bzl
  ln -s @PLUGIN@/external_plugin_deps.bzl .
```

From Gerrit source tree issue the command:

```
  bazel build plugins/@PLUGIN@
```

The output is created in

```
  bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar
```

To execute the tests run either one of:

```
  bazel test --test_tag_filters=@PLUGIN@ //...
  bazel test plugins/@PLUGIN@:@PLUGIN@_tests
```

This project can be imported into the Eclipse IDE:
Add the plugin name to the `CUSTOM_PLUGINS` and to the
`CUSTOM_PLUGINS_TEST_DEPS` set in Gerrit core in
`tools/bzl/plugins.bzl`, and execute:

```
  ./tools/eclipse/project.py
```

How to build the Gerrit Plugin API is described in the [Gerrit
documentation](../../../Documentation/dev-bazel.html#_extension_and_plugin_api_jar_files).

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
