---
layout: default
title: Connection API
nav_order: 14
---

# Connection API

The Connection Service is a component at the service layer of the BDK which aims to cover the Connections part of the [REST API documentation](https://developers.symphony.com/restapi/main/connections).
More precisely:
* [Get connection](https://developers.symphony.com/restapi/main/connections/get-connection)
* [List connections](https://developers.symphony.com/restapi/main/connections/list-connections)
* [Create connection](https://developers.symphony.com/restapi/main/connections/create-connection)
* [Accept connection](https://developers.symphony.com/restapi/main/connections/accepted-connection)
* [Reject connection](https://developers.symphony.com/restapi/main/connections/reject-connection)
* [Remove connection](https://developers.symphony.com/restapi/main/connections/remove-connection)


## How to use
The central component for the Connection Service is the `ConnectionService` class.
This class exposes the user-friendly service APIs which serve all the services mentioned above
and is accessible from the `SymphonyBdk` object by calling the `connections()` method:


## How to use
The central component for the Connection Service is the `ConnectionService` class.
This class exposes the user-friendly service APIs which serve all the services mentioned above
and is accessible from the `SymphonyBdk` object by calling the `connections()` method:
```java
@Slf4j
public class Example {
  public static final Long USER_ID = 123456789L;

  public static void main(String[] args) throws Exception {
    // Create BDK entry point
    final SymphonyBdk bdk = new SymphonyBdk(loadFromClasspath("/config.yaml"));

    // Get connection status
    ConnectionService connections = bdk.connections();
    UserConnection connection = connections.getConnection(123L);
    log.info("Connection status: " + connection.getStatus());
  }
}
```
