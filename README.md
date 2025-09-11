# Gerrit high-availability plugin

This plugin allows deploying a cluster of multiple Gerrit primaries
on the same data-center sharing the same Git repositories.

Requirements for the Gerrit primaries are:

- Externally mounted filesystem shared among the cluster
- Load-balancer (HAProxy or similar)

## License

This plugin is released under the same Apache 2.0 license and copyright holders
as of the Gerrit Code Review project.

## How to build

Refer to the [build instructions in the plugin documentation](src/main/resources/Documentation/build.md).

## Sample configuration for two Gerrit primaries in high-availability

Assuming that the Gerrit primaries in the clusters are `gerrit-01.mycompany.com` and
`gerrit-02.mycompany.com`, listening on the HTTP port 8080, with a shared volume
mounted under `/shared`, see below the minimal configuration steps.

1. Install one Gerrit primary on the first node (e.g. `gerrit-01.mycompany.com`)
   with the repositories location under the shared volume (e.g. `/shared/git`).
   Init the site in order to create the initial repositories.

2. Copy all the files of the first Gerrit primary onto the second node (e.g. `gerrit-02.mycompany.com`)
   so that it points to the same repositories location.

3. Install the high-availability plugin into the `$GERRIT_SITE/plugins` directory of both
   the Gerrit servers.

4. On `gerrit-01.mycompany.com`, create the `$GERRIT_SITE/etc/high-availability.config` with
   the following settings:

   ```
   [main]
     sharedDirectory = /shared

   [peerInfo]
     strategy = static

   [peerInfo "static"]
     url = http://gerrit-02.mycompany.com:8080
   ```

5. On `gerrit-02.mycompany.com`, create the `$GERRIT_SITE/etc/high-availability.config` with
   the following settings:

   ```
   [main]
     sharedDirectory = /shared

   [peerInfo]
     strategy = static

   [peerInfo "static"]
     url = http://gerrit-01.mycompany.com:8080
   ```

For more details on the configuration settings, please refer to the
[high-availability configuration documentation](src/main/resources/Documentation/config.md).

## Load-balancing configuration

It is possible to distribute the incoming traffic to both Gerrit nodes using any software that can
perform load-balancing of the incoming connections.

The load-balancing of the HTTP traffic is at L7 (Application) while the
SSH traffic is balanced at L4 (Transport) level.

### Active-passive configuration

It is the simplest and safest configuration, where only one Gerrit primary at a
time serves the incoming requests.
In case of failure of the primary, the traffic is forwarded to the backup.

Assuming a load-balancing implemented using [HAProxy](http://www.haproxy.org/)
associated with the domain name `gerrit.mycompany.com`, exposing Gerrit cluster nodes
on ports, 80 (HTTP) and 29418 (SSH), see below the minimal configuration
steps

Add to the `haproxy.cfg` the frontend configurations associated with the HTTP
and SSH services:

```
frontend gerrit_http
    bind *:80
    mode http
    default_backend gerrit_http_nodes

frontend gerrit_ssh
    bind *:29418
    mode tcp
    default_backend gerrit_ssh_nodes
```

Add to the `haproxy.cfg` the backend configurations pointing to the Gerrit cluster
nodes:

```
backend gerrit_http_nodes
    mode http
    balance source
    option forwardfor
    default-server inter 10s fall 3 rise 2
    option httpchk GET /config/server/version HTTP/1.0
    http-check expect status 200
    server gerrit_http_01 gerrit-01.mycompany.com:8080 check inter 10s
    server gerrit_http_02 gerrit-01.mycompany.com:8080 check inter 10s backup

backend gerrit_ssh_nodes
    mode tcp
    option httpchk GET /config/server/version HTTP/1.0
    http-check expect status 200
    balance source
    timeout connect 10s
    timeout server 5m
    server gerrit_ssh_01 gerrit-01.mycompany.com:29418 check port 8080 inter 10s fall 3 rise 2
    server gerrit-ssh_02 gerrit-02.mycompany.com:29418 check port 8080 inter 10s fall 3 rise 2 backup
```

### Active-active configuration

This is an evolution of the previous active-passive configuration, where only one Gerrit primary at a
time serves the HTTP write operations (PUT,POST,DELETE) while the remaining HTTP traffic is sent
to both.
In case of failure of one of the nodes, all the traffic is forwarded to the other node.

With regards to the SSH traffic, it cannot be safely sent to both nodes because it is associated
with a stateful session that can host multiple commands of different nature.

Assuming an active-passive configuration using HAProxy, see below the changes needed to implement
an active-active scenario.

Add to the `haproxy.cfg` the extra acl settings into the `gerrit_http` frontend configurations
associated with the HTTP and SSH services:

```
frontend gerrit_http
    bind *:80
    mode http
    acl http_writes method PUT POST DELETE PATCH
    use_backend gerrit_http_nodes if http_writes
    default_backend gerrit_http_nodes_balanced
```

Add to the `haproxy.cfg` a new backend for serving all read-only HTTP operations from both nodes:

```
backend gerrit_http_nodes_balanced
    mode http
    balance source
    option forwardfor
    default-server inter 10s fall 3 rise 2
    option httpchk GET /config/server/version HTTP/1.0
    http-check expect status 200
    server gerrit_http_01 gerrit-01.mycompany.com:8080 check inter 10s
    server gerrit_http_02 gerrit-01.mycompany.com:8080 check inter 10s
```

## Gerrit canonical URL and further adjustments

Both Gerrit primaries are now part of the same cluster, accessible through the HAProxy load-balancer.
Set the `gerrit.canoncalWebUrl` on both Gerrit primaries to the domain name of HAProxy so that any
location or URL generated by Gerrit would direct the traffic to the balancer and not to the instance
that served the incoming call.

Example:
```
[gerrit]
  canonicalWebUrl = http://gerrit.mycompany.com
```

Secondly, adjust the HTTP listen configuration adding the `proxy-` prefix, to inform Gerrit that
the traffic is getting filtered through a reverse proxy.

Example:
```
[httpd]
  listenUrl = proxy-http://*:8080/
```

Last adjustment is associated with the session cookies, because they would need to be bound to
the domain rather than the individual node.

Example:
```
[auth]
  cookiedomain = .mycompany.com
```
