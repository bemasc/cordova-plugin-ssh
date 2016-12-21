package org.uproxy.cordovasshplugin;

import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.DynamicPortForwarder;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import org.uproxy.cordovasshplugin.SshPluginService;

public class SshWrapper {
  private static final String LOG_TAG = "SshWrapper";

  private static final int STATE_INACTIVE = 0;
  private static final int STATE_CONNECTED = 1;
  private static final int STATE_PROXYING = 2;
  private static final int STATE_DISCONNECTED = 3;

  private Connection connection = null;
  private DynamicPortForwarder proxy = null;

  private final SshPluginService service;
  private final int id;
  private JSONObject info = new JSONObject();
  private SshConnectionMonitor monitor = new SshConnectionMonitor();

  SshWrapper(SshPluginService service, int id) {
    this.service = service;
    this.id = id;
    try {
      info.put("state", STATE_INACTIVE);
    } catch (JSONException e) {
      Log.e(LOG_TAG, e.toString());
    }
  }

  public void execute(Intent request) {
    String command = request.getStringExtra("command");
    if ("connect".equals(command)) {
      connect(request);
    } else if ("disconnect".equals(command)) {
      disconnect(request);
    } else if ("startProxy".equals(command)) {
      startProxy(request);
    } else if ("stopProxy".equals(command)) {
      stopProxy(request);
    } else if ("getInfo".equals(command)) {
      getInfo(request);
    }
  }

  private void connect(final Intent request) {
    if (connection != null) {
      service.reject(request, "Already connected");
      return;
    }
    final String host = request.getStringExtra("host");
    final int port = request.getIntExtra("port", -1);
    final String user = request.getStringExtra("user");
    final String key = request.getStringExtra("key");
    final String password = request.getStringExtra("password");

    try {
      info.put("host", host);
      info.put("port", port);
      info.put("user", user);
      info.put("key", key);
      info.put("password", password);
    } catch (JSONException e) {
      Log.e(LOG_TAG, e.toString());
      service.reject(request, e.toString());
      return;
    }

    connection = new Connection(host, port);
    connection.addConnectionMonitor(monitor);
    new Thread(
      new Runnable() {
        public void run() {
          try {
            if (initiateConnection()) {
              changeState(STATE_CONNECTED);
              service.fulfill(request);
            } else {
              service.reject(request, "Connection failed");
            }
          } catch (Exception e) {
            service.reject(request, e.toString());
          }
        }
      }, "Initiate connection")
    .start();
  }

  private boolean initiateConnection() throws Exception {
    String host = info.optString("host");
    int port = info.optInt("port");
    String user = info.optString("user");
    String key = info.optString("key");
    String password = info.optString("password");

    if (connection == null) {
      throw new NullPointerException("Connection disappeared!");
    }

    String nonNullPassword = "";
    if (password != null) {
      nonNullPassword = password;
    }
    boolean success = false;
    ConnectionInfo info = connection.connect();
    if (key == null || key.isEmpty()) {
      success = connection.authenticateWithPassword(user, nonNullPassword);
    } else {
      byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
      char[] keyChars = new char[keyBytes.length];
      for (int i = 0; i < keyBytes.length; ++i) {
        keyChars[i] = (char) keyBytes[i];
      }
      success = connection.authenticateWithPublicKey(user, keyChars, nonNullPassword);
    }
    return success;
  }

  private void disconnect(final Intent request) {
    // connection.close() does network operations so it has to happen off-thread.
    new Thread(
      new Runnable() {
        public void run() {
          connection.close();
          connection = null;
          changeState(STATE_INACTIVE);
          service.fulfill(request);
        }
      }, "Disconnect")
    .start();
  }

  private void startProxy(final Intent request) {
    final int port = request.getIntExtra("port", -1);
    try {
      info.put("proxyPort", port);
    } catch (JSONException e) {
      Log.e(LOG_TAG, e.toString());
      service.reject(request, e.toString());
      return;
    }

    try {
      proxy = connection.createDynamicPortForwarder(port);
      changeState(STATE_PROXYING);
      service.fulfill(request);
    } catch (IOException e) {
      Log.e(LOG_TAG, e.toString());
      service.reject(request, e.toString());
    }
  }

  private void stopProxy(final Intent request) {
    boolean success = false;
    if (proxy == null) {
      String msg = "No proxy to stop";
      Log.e(LOG_TAG, msg);
      service.reject(request, msg);
    } else {
      try {
        proxy.close();
        proxy = null;
        changeState(STATE_CONNECTED);
        service.fulfill(request);
      } catch (IOException e) {
        Log.e(LOG_TAG, e.toString());
        service.reject(request, e.toString());
      }
    }
  }

  private void getInfo(final Intent request) {
    service.fulfill(request, info);
  }

  private void changeState(int state) {
    try {
      info.put("state", state);
      service.emit(this.id, "onStateChange", state);
    } catch (JSONException e) {
      Log.e(LOG_TAG, e.toString());
    }
  }

  private class SshConnectionMonitor implements ConnectionMonitor {
    @Override
    public void connectionLost(Throwable reason) {
      changeState(STATE_DISCONNECTED);

      // Retry loop
      new Thread(
        new Runnable() {
          public void run() {
            try {
              connection.close();
              while (info.optInt("state") == STATE_DISCONNECTED) {
                Thread.sleep(15000);
                if (initiateConnection()) {
                  if (proxy != null) {
                    // Recreate the proxy on the new connection.
                    int port = info.optInt("proxyPort");
                    proxy = connection.createDynamicPortForwarder(port);
                    changeState(STATE_PROXYING);
                  } else {
                    changeState(STATE_CONNECTED);
                  }
                  break;
                }
              }
            } catch (Exception e) {
              Log.e(LOG_TAG, "Abandoning retry loop: " + e.toString());
            }
          }
        }, "Retry loop")
      .start();
    }
  }
}
