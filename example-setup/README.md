# What is this for?

This docker compose set up Gerrit installation with HA proxy and 2 nodes with HA plugin. 

To spin up your environment:
- Copy the `high-availability` artefact to `gerrit` directory:

```
cp $GERRIT_HOME/bazel-bin/plugins/high-availability/high-availability.jar gerrit
```

- Build Gerrit image

```
make build
```

- Run HA installation with HA proxy active-active

```
make up-active-active
```

- Open the browser

```
make open
```

In order to stop and clean the environment:

```
make clean
```
