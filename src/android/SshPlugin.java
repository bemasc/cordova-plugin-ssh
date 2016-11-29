package org.uproxy.cordovasshplugin;

import android.util.Base64;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.DynamicPortForwarder;

import java.io.IOException;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SshPlugin extends CordovaPlugin {
    private Connection connection = null;
    private DynamicPortForwarder proxy = null;

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("connectWithPublicKey")) {
            String host = args.getString(0);
            int port = args.getInt(1);
            String user = args.getString(2);
            String key = args.getString(3);
            // TODO: hostKey
            connectWithPublicKey(host, port, user, key, callbackContext);
            return true;
        } else if (action.equals("connectWithPassword")) {
            String host = args.getString(0);
            int port = args.getInt(1);
            String user = args.getString(2);
            String password = args.getString(3);
            // TODO: hostKey
            connectWithPassword(host, port, user, password, callbackContext);
            return true;
        } else if (action.equals("disconnect")) {
            disconnect(callbackContext);
            return true;
        } else if (action.equals("startProxy")) {
            int port = args.getInt(0);
            startProxy(port, callbackContext);
            return true;
        } else if (action.equals("stopProxy")) {
            stopProxy(callbackContext);
            return true;
        }
        return false;
    }

    private void connectWithPublicKey(final String host, final int port, final String user, final String key, final CallbackContext callbackContext) {
      connection = new Connection(host, port);
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          if (connection == null) {
            callbackContext.error("Connection disappeared!");
            return;
          }

          byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
          char[] keyChars = new char[keyBytes.length];
          for (int i = 0; i < keyBytes.length; ++i) {
            keyChars[i] = (char) keyBytes[i];
          }
          try {
              ConnectionInfo info = connection.connect();
              String password = "";
              if (connection.authenticateWithPublicKey(user, keyChars, password)) {
                callbackContext.success();
              } else {
                callbackContext.error("Authentication failed");
              }
          } catch (IOException e) {
              callbackContext.error(e.toString());
          }
        }
      });
    }

    private void connectWithPassword(final String host, final int port, final String user, final String password, final CallbackContext callbackContext) {
      connection = new Connection(host, port);
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          if (connection == null) {
            callbackContext.error("Connection disappeared!");
            return;
          }

          try {
              ConnectionInfo info = connection.connect();
              if (connection.authenticateWithPassword(user, password)) {
                callbackContext.success();
              } else {
                callbackContext.error("Authentication failed");
              }
          } catch (IOException e) {
              callbackContext.error(e.toString());
          }
        }
      });
    }

    private void disconnect(final CallbackContext callbackContext) {
      if (connection == null) {
        callbackContext.error("No connection to close");
        return;
      }
      connection.close();
      connection = null;
      callbackContext.success();
    }

    private void startProxy(final int port, final CallbackContext callbackContext) {
      try {
        proxy = connection.createDynamicPortForwarder(port);
        callbackContext.success();
      } catch (IOException e) {
        callbackContext.error(e.toString());
      }
    }

    private void stopProxy(final CallbackContext callbackContext) {
      if (proxy == null) {
        callbackContext.error("No proxy to stop");
        return;
      }
      try {
        proxy.close();
        proxy = null;
        callbackContext.success();
      } catch (IOException e) {
        callbackContext.error(e.toString());
      }
    }
}
