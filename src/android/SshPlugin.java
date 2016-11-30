package org.uproxy.cordovasshplugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.DynamicPortForwarder;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.uproxy.cordovasshplugin.SshPluginService;

public class SshPlugin extends CordovaPlugin {
    private Connection connection = null;
    private DynamicPortForwarder proxy = null;

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {

        if (action.equals("connect")) {
            String host = args.getString(0);
            int port = args.getInt(1);
            String user = args.getString(2);
            String key = args.getString(3);
            String password = args.getString(4);
            // TODO: hostKey
            connect(host, port, user, key, password, callbackContext);
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

    private void connect(final String host, final int port, final String user, final String key, final String password, final CallbackContext callbackContext) {
      listenOnce("connectResult", callbackContext);

      Context context = this.cordova.getActivity();
      Intent connectIntent = new Intent(context, SshPluginService.class);
      connectIntent.putExtra("host", host);
      connectIntent.putExtra("port", port);
      connectIntent.putExtra("user", user);
      connectIntent.putExtra("key", key);
      connectIntent.putExtra("password", password);
      context.startService(connectIntent);

    }

    private void disconnect(final CallbackContext callbackContext) {
      Context context = this.cordova.getActivity().getApplicationContext(); 
      Intent stopIntent = new Intent(context, SshPluginService.class);
      context.stopService(stopIntent);
      callbackContext.success();
    }

    private void startProxy(final int port, final CallbackContext callbackContext) {
      listenOnce("startProxyResult", callbackContext);

      Intent startProxyIntent = new Intent("startProxy");
      startProxyIntent.putExtra("port", port);
      sendIntent(startProxyIntent);
    }

    private void stopProxy(final CallbackContext callbackContext) {
      listenOnce("stopProxyResult", callbackContext);
      sendIntent(new Intent("stopProxy"));
    }

  private void sendIntent(final Intent intent) {
    Context context = this.cordova.getActivity().getApplicationContext(); 
    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }

  private void listenOnce(final String intentName, final CallbackContext callbackContext) {
    BroadcastReceiver receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        boolean success = intent.getBooleanExtra("success", false);
        if (success) {
          callbackContext.success();
        } else {
          callbackContext.error("Failed");
        }
        // Single-use receiver.
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
      }
    };

    Context context = this.cordova.getActivity().getApplicationContext(); 
    LocalBroadcastManager.getInstance(context).registerReceiver(
        receiver, new IntentFilter(intentName));
  }
}
