# Luna Netty Plugin

The `luna-netty-plugin` is the main server implementation on top of [Netty](http://netty.io). The following services
will be provided by this plugin:

 * `io.lunamc.server.Server` using `luna-protocol`
 * `io.lunamc.server.ServerConfiguration` using a xml-based backend
 * `io.lunamc.login.session.SessionClient` using
   [async-http-client](https://github.com/AsyncHttpClient/async-http-client)
 * `io.lunamc.plugins.netty.netty.EventLoopGroupHolder`
 * `io.lunamc.plugins.netty.netty.GlobalEventExecutorController` (internal use only)

The plugin requires the following service implementations:

 * `io.lunamc.common.login.encryption.EncryptionFactory` ([luna-common](https://github.com/lunamc/common))
 * `io.lunamc.common.json.JsonMapper` ([luna-common](https://github.com/lunamc/common))
 * `io.lunamc.common.host.VirtualHostManager` ([luna-common](https://github.com/lunamc/common))
 * `io.lunamc.plugins.netty.handler.PlayHandlerFactory` ([luna-example](https://github.com/lunamc/plugin-example))

## Registration

```xml
<plugin file="plugins/luna-netty-plugin-0.0.1-SNAPSHOT.jar" id="luna-netty">
    <security>
        <permissions>
            <permission impl="java.util.PropertyPermission" name="*" action="read" />
            <permission impl="java.lang.RuntimePermission" name="modifyThread" action="" />
            <permission impl="java.lang.RuntimePermission" name="accessDeclaredMembers" action="" />
            <permission impl="java.lang.RuntimePermission" name="accessClassInPackage.sun.misc" action="" />
            <permission impl="java.lang.RuntimePermission" name="getClassLoader" action="" />
            <permission impl="java.lang.reflect.ReflectPermission" name="suppressAccessChecks" action="" />
            <permission impl="java.net.SocketPermission" name="*" action="listen,accept,resolve" />
            <permission impl="java.io.FilePermission" name="${java.io.tmpdir}" action="read,write,delete" />
        </permissions>
    </security>
</plugin>
```

## XML Schema Definition

This repository also contains the xsd file used for server configuration (version: `1.0`). You can find the source file
[here](./src/resources/xsd/server-1.0.xsd). The namespace is `http://lunamc.io/server/1.0`.

It is online available at:

> http://static.lunamc.io/xsd/server-1.0.xsd

or if you're preferring https:

> https://s3.eu-central-1.amazonaws.com/static.lunamc.io/xsd/server-1.0.xsd
