# cordova-plugin-ssh

This Cordova plugin provides the ability to start an SSH connection and use it
as a proxy.  The SSH connection runs in a Service, so it will outlive the main
Activity.

The SSH implementation is [Connectbot's fork of trilead's SSH library](https://github.com/connectbot/sshlib),
which has over 1 million users on the Play Store.

### Android only

This plugin currently only supports Android.

### Javascript API

This plugin provides a class representing an SSH connection, exposed as
```typescript
new cordova.plugins.SshPlugin.Connection();
```

It has the following interface:
```typescript
interface Connection {
  // Connect to the destination by private key, password, or both
  connect(host:string, port:number, user:string, privateKey:string, password:string) : Promise<void>;

  // Disconnect from the destination (and stop the proxy if it is running).
  disconnect(): Promise<void>;

  // Start a SOCKS5 proxy on a specified port on localhost, forwarding through the
  // SSH tunnel.  This can only be called after connection succeeds.
  // Returns the proxy port number. If |port| is 0 or absent, this will be chosen
  // automatically.
  startProxy(port?:number): Promise<number>;

  // Stop the SOCKS5 proxy but leave the connection open.
  stopProxy(): Promise<void>;

  // Get the ID of this connection.
  getId(): Promise<number>;

  // Get information about the state of this connection.
  getConnectionInfo(): Promise<{state:number; host:string; port:number; user:string;}

  // Register an event listener, to be called when state changes occur.
  onStateChange(listener:(state:number) => void): void;
}
```

Additionally, `Connection` has some important static attributes

 * `Connection.State` is an enum of number-valued states.
 * `Connection.getAll()` returns a list of `Connection` objects representing all extant connections.

### TODO

 * Use network state events to avoid polling for reconnection.
 * Wake up the app if something happens while the Activity is not active
 * Remove requirement that the WebView supports `Promise`.
