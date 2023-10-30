# What is this for?

This docker compose set up HA installation with HA proxy and 2 nodes. 

To spin up your environment:
1) copy the `high-availability` artefact to `gerrit` directory:

```bash
cp $GERRIT_HOME/bazel-bin/plugins/high-availability/high-availability.jar gerrit
```

2) build Gerrit image

```bash
make build
```

3) run HA installation with HA proxy active-active

```bash
make nup-active-active
```

4) clean the environment

```bash
make clean
```
