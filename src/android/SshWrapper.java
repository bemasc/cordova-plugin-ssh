package org.uproxy.cordovasshplugin;

import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
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

  final SshPluginService service;
  final int id;
  JSONObject info = new JSONObject();

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
    } catch (JSONException e) {
      Log.e(LOG_TAG, e.toString());
      service.reject(request, e.toString());
      return;
    }

    connection = new Connection(host, port);
    new Thread(
      new Runnable() {
        public void run() {
          if (connection == null) {
            String msg = "Connection disappeared!";
            Log.e(LOG_TAG, msg);
            service.reject(request, msg);
            return;
          }

          String nonNullPassword = "";
          if (password != null) {
            nonNullPassword = password;
          }
          boolean success = false;
          try {
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
          } catch (IOException e) {
            Log.e(LOG_TAG, e.toString());
            service.reject(request, e.toString());
            return;
          }

          if (success) {
            changeState(STATE_CONNECTED);
            service.fulfill(request);
          } else {
            service.reject(request, "Connection failed");
          }
        }
      }, "Initiate connection")
    .start();
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
}
