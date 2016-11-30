# cordova-plugin-ssh

This Cordova plugin provides the ability to start an SSH connection and use it
as a proxy.  The SSH connection runs in a Service, so it will outlive the main
Activity.

The SSH implementation is [Connectbot's fork of trilead's SSH library](https://github.com/connectbot/sshlib),
which has over 1 million users on the Play Store.

### Android only

This plugin currently only supports Android.

### Javascript API

```typescript
connect(host:string, port:number, username:string, privateKey:string, password:string) : Promise<void>;
```

Connects to the server and authenticates with the private key (if provided)
and/or password (if provided).

```typescript
disconnect(): Promise<void>;
```

Disconnects from the server

```typescript
startProxy(port): Promise<void>
```

Starts a SOCKS5 proxy running on the specified port on `localhost`, forwarding
through the SSH tunnel.  Call `connect()` first and wait for it to succeed;
otherwise this function will fail.

```typescript
stopProxy(): Promise<void>
```
Stop the SOCKS5 proxy.  The connection remains open.

### TODO

 * Reconnect after transient disconnections
 * Provide automatic port selection if the user calls `startProxy(0)`
 * Allow multiple active connections
 * Notify the app if the connection fails
 * Wake up the app if something happens while the Activity is not active
