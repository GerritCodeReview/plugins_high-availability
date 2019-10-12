# Gerrit high-availability setup example

This Docker Compose project contains a simple test environment
of two Gerrit masters in HA configuration.

## How to build

The project can be built using docker-compose.

To build the Docker VMs:
```
  $ docker-compose build
```

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

If you want to stop and cleanup all the previous state, use the 'stop'
target.

```
  $ docker-compose down
```

