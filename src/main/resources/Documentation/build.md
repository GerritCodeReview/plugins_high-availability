Build
=====

This plugin can be built with Bazel in the Gerrit source tree.

Clone or link this plugin into the `plugins` directory of the Gerrit
source tree. Then link the plugin's module fragment into Gerrit's
`plugins` directory, replacing the placeholder file. This fragment
exposes the plugin's Bazel module and its external dependencies to
the Gerrit root module when building in-tree.

```
  cd gerrit/plugins
  rm external_plugin_deps.MODULE.bazel
  ln -s @PLUGIN@/external_plugin_deps.MODULE.bazel .
```

From the Gerrit source tree run:

```
  bazel build plugins/@PLUGIN@
```

The output is created in:

```
  bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar
```

To execute the tests run either of:

```
  bazel test plugins/@PLUGIN@/...
  bazel test --test_tag_filters=@PLUGIN@ //...
  bazel test plugins/@PLUGIN@:@PLUGIN@_tests
```

This project can also be imported into the Eclipse IDE.

Add the plugin name to the `CUSTOM_PLUGINS` and
`CUSTOM_PLUGINS_TEST_DEPS` sets in Gerrit core in
`tools/bzl/plugins.bzl`, and execute:

```
  ./tools/eclipse/project.py
```

How to build the Gerrit Plugin API is described in the
[Gerrit documentation](../../../Documentation/dev-bazel.html#_extension_and_plugin_api_jar_files).

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
