# Gerrit high-availability setup example

This Docker Compose project contains a simple test environment
of two Gerrit masters in HA configuration.

## How to build

The project can be built using docker-compose.

To build the Docker VMs:
```
  $ docker-compose build
```

### Building the Docker VMs using a non-default user id

```
  $ export GERRIT_UID=$(id -u)
  $ docker-compose build --build-arg GERRIT_UID
```

Above, exporting that UID is optional and will be 1000 by default.
Build the gerrit images this way only if the user with id 1000 on your
host is not owned by you. For example, some corporate environments use a
restricted 1000 user (id). In that case, the containerized application
may fail to write towards the host (through volumes).

That UID will be the one set for the containerized gerrit user. Latter's
group will remain as default (1000). This is because groups known from
the host need to be redefined for containers. Setting that user's group
in the container is not necessary for writing anyway, as opposed to its
user id. The individual gerrit user's writing permission does suffice.

## How to run

Use the 'up' target to startup the Docker Compose VMs.

```
  $ docker-compose up
```

## How to stop

Simply type CTRL+C on the window that started the environment
and all the VMs will stop. Their state will be persisted and the next
run will continue with the same data.

## How to clean

If you want to stop and cleanup all the previous state, use the 'down'
target.

```
  $ docker-compose down
```

