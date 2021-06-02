# Gerrit high-availability docker setup example

The Docker Compose project in the docker directory contains a simple test 
environment of two Gerrit masters in HA configuration.

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

# Gerrit high-availability local setup example

 1. Init gerrit instances with high-availability plugin installed:
    1. Optionally, set http port of those instance to 8081 and 8082.
    2. Make sure ssh ports on those instances are different. (i.e. 29418 and 29419)
    3. Make sure instances share the same git repo.
    4. Create and provide shared directory to those instances.
 2. Set up high-availability plugin.
    1. main.sharedDirectory = "the created shared directory above".
    2. peerInfo.strategy = static
    3. peerInfo "static".url = other_instance_url (i.e http://localhost:8081 or http://localhost:8082)

## How to test

Consider the
[instructions](https://gerrit-review.googlesource.com/Documentation/dev-e2e-tests.html)
on how to use Gerrit core's Gatling framework, to run non-core test
scenarios such as this plugin one below:

```
  $ sbt "gatling:testOnly com.ericsson.gerrit.plugins.highavailability.scenarios.CloneUsingHAGerrit2"
```

This is a scenario that can serve as an example for how to start
testing an HA Gerrit system. That scenario tries to clone a project
created on gerrit 1 (port 8081) but from gerrit 2 (on 8082). The
scenario therefore expects Gerrit HA to have properly synchronized
the new project from 1 to 2. That project gets deleted after, here
using HA Gerrit straight (through default http port 80).

Scenario scala source files and their companion json resource ones are
stored under the usual src/test directories. That structure follows the
scala package one from the scenario classes. The core framework expects
such a directory structure for both the scala and resources (json data)
files.

Alternatively, the TEST_HA script can be used to run Gatling tests which
provides a minimum configuration to run the test.

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
