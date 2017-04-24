# Build

This plugin can be built with Bazel or Buck and two build modes are supported:

* Standalone
* In Gerrit tree

Standalone build mode is recommended, as this mode doesn't require local Gerrit
tree to exist. Moreover, there are some limitations and additional manual steps
required when building in Gerrit tree mode (see corresponding sections).

## Build standalone

### Bazel

To build the plugin, issue the following command:

```
  bazel build @PLUGIN@
```

The output is created in

```
  bazel-genfiles/@PLUGIN@.jar
```

To package the plugin sources run:

```
  bazel build lib@PLUGIN@__plugin-src.jar
```

The output is created in:

```
  bazel-bin/lib@PLUGIN@__plugin-src.jar
```

To execute the tests run:

```
  bazel test high_availability_tests
```

This project can be imported into the Eclipse IDE:

```
  ./tools/eclipse.py
```

### Buck

Clone bucklets library:

```
  git clone https://gerrit.googlesource.com/bucklets

```

and link it to @PLUGIN@ directory:

```
  cd @PLUGIN@ && ln -s ../bucklets .
```

Add link to the .buckversion file:

```
  cd @PLUGIN@ && ln -s bucklets/buckversion .buckversion
```

Add link to the .watchmanconfig file:

```
  cd @PLUGIN@ && ln -s bucklets/watchmanconfig .watchmanconfig
```

To build the plugin, issue the following command:

```
  buck build plugin
```

The output is created in:

```
  buck-out/gen/@PLUGIN@.jar
```

This project can be imported into the Eclipse IDE:

```
  ./bucklets/tools/eclipse.py
```

To execute the tests run:

```
  buck test
```

To build plugin sources run:

```
  buck build src
```

The output is created in:

```
  buck-out/gen/@PLUGIN@-sources.jar
```

## Build in Gerrit tree

### Bazel

Clone or link this plugin to the plugins directory of Gerrit's source tree, and
issue the command:

```
  bazel build plugins/@PLUGIN@
```

The output is created in

```
  bazel-genfiles/plugins/@PLUGIN@/@PLUGIN@.jar
```

This project can be imported into the Eclipse IDE. First, list the plugin in the
custom plugin list, in `gerrit/tools/bzl/plugins.bzl`:

```
CUSTOM_PLUGINS = [
  ...
  '@PLUGIN@',
]
```

and issue the command:

```
  ./tools/eclipse/project_bzl.py
```

* Note: wiremock and mockito jars should be added manually to classpath. In
Eclipse:
`Project -> Java Build Path -> Add External JARS`

To execute the tests, Gerrit WORKSPACE should be patched:

```
  cat plugins/sync-index/WORKSPACE.in_gerrit_tree >> WORKSPACE
```

then run:

```
  bazel test plugins/@PLUGIN@:sync_index_tests
```

### Buck

Clone or link this plugin to the plugins directory of Gerrit's source
tree, and issue the command:

```
  buck build plugins/@PLUGIN@
```

The output is created in:

```
  buck-out/gen/plugins/@PLUGIN@/@PLUGIN@.jar
```

This project can be imported into the Eclipse IDE:

```
  ./tools/eclipse/project.py
```

* Note: wiremock and mockito jars should be added manually to classpath. In
Eclipse:
`Project -> Java Build Path -> Add External JARS`


To execute the tests run:

```
  buck test --include @PLUGIN@
```

How to build the Gerrit Plugin API is described in the [Gerrit
documentation](../../../Documentation/dev-buck.html#_extension_and_plugin_api_jar_files).

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
