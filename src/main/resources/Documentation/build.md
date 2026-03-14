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

### Packaged runtime JAR allowlist test

This plugin tracks the set of third-party runtime JARs that are bundled into the plugin JAR.
A deterministic, version-agnostic manifest is generated from the plugin’s runtime classpath and
compared against the checked-in allowlist:

`high-availability_third_party_runtime_jars.allowlist.txt`

This acts as a guardrail to detect unintended changes to the packaged runtime dependency set.

To run the check in Gerrit tree:

```bash
  bazel test plugins/@PLUGIN@:check_high-availability_third_party_runtime_jars
```

#### Updating the allowlist

If the test fails because the packaged third-party JAR set changed, the plugin’s bundled runtime
dependencies have changed.

If the change is expected and has been reviewed, refresh the allowlist:

```bash
  bazel build plugins/@PLUGIN@:check_high-availability_third_party_runtime_jars_manifest
  cp bazel-bin/plugins/@PLUGIN@/check_high-availability_third_party_runtime_jars_manifest.txt \
  plugins/@PLUGIN@/high-availability_third_party_runtime_jars.allowlist.txt
```

Commit the updated allowlist along with the dependency change.

### Gerrit-tree-only plugin checks

This plugin contains additional guardrail tests that are meaningful only
when it is built inside the Gerrit source tree (e.g. checks comparing the
plugin’s packaged runtime jars against Gerrit’s own runtime classpath).

Then execute:

```bash
  bazel test plugins/@PLUGIN@:high-availability_no_overlap_with_gerrit
```

How to build the Gerrit Plugin API is described in the
[Gerrit documentation](../../../Documentation/dev-bazel.html#_extension_and_plugin_api_jar_files).

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
