package org.uproxy.cordovasshplugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.uproxy.cordovasshplugin.SshPluginService;

public class SshPlugin extends CordovaPlugin {
  private static final String LOG_TAG = "SshPlugin";

  private Map<Pair<Integer, String>, CallbackContext> listeners = new HashMap();

  private List<Intent> pendingCommands = new ArrayList();

  private boolean serviceIsUp = false;
  ServiceConnection serviceWatcher = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      Log.i(LOG_TAG, "Service is up.  Draining " + pendingCommands.size() + " pending commands.");
      serviceIsUp = true;
      for (Intent i: pendingCommands) {
        sendIntent(i);
      }
      pendingCommands.clear();
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      serviceIsUp = false;
    }
  };

  BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      int connectionId = intent.getIntExtra("connectionId", -1);
      String requestId = intent.getStringExtra("requestId");
      Pair<Integer, String> key = new Pair(connectionId, requestId);
      if (!listeners.containsKey(key)) {
        return;
      }

      CallbackContext callbackContext = listeners.get(key);

      boolean success = intent.getBooleanExtra("success", false);
      String payload = intent.getStringExtra("payload");
      PluginResult.Status status = success ? PluginResult.Status.OK : PluginResult.Status.ERROR;
      PluginResult result = new PluginResult(status, payload);
      if (requestId.equals("onStateChange")) {
        // Perennial listener for events.
        result.setKeepCallback(true);
      } else {
        // Single-use listener for a promise command.
        listeners.remove(key);
      }
      callbackContext.sendPluginResult(result);
    }
  };

  @Override
  protected void pluginInitialize() {
    Context context = this.cordova.getActivity();
    Intent intent = new Intent(context, SshPluginService.class);
    context.startService(intent);

    LocalBroadcastManager.getInstance(context).registerReceiver(
        receiver, new IntentFilter("fromService"));

    // Wait (asynchronously) for service to come up.
    context.bindService(intent, serviceWatcher, Context.BIND_AUTO_CREATE);
  }

  @Override
  public boolean execute(String command, JSONArray args, final CallbackContext callbackContext) throws JSONException {
    int connectionId = -1;  // -1 means "not for any particular connection"
    if (!command.equals("getNewConnection") &&
        !command.equals("getIds")) {
      connectionId = args.getInt(0);
    }
    if (command.equals("onStateChange")) {
      return addListener(connectionId, "onStateChange", callbackContext);
    }

    Intent i = new Intent("toService");
    i.putExtra("command", command);
    i.putExtra("connectionId", connectionId);
    String requestId = UUID.randomUUID().toString();
    i.putExtra("requestId", requestId);
    if (command.equals("connect")) {
      i.putExtra("host", args.getString(1));
      i.putExtra("port", args.getInt(2));
      i.putExtra("user", args.getString(3));
      i.putExtra("key", args.getString(4));
      i.putExtra("password", args.getString(5));
      // TODO: hostKey
    } else if (command.equals("disconnect")) {
    } else if (command.equals("startProxy")) {
      i.putExtra("port", args.getInt(1));
    } else if (command.equals("stopProxy")) {
    } else if (command.equals("getInfo")) {
    } else if (command.equals("getIds")) {
    } else if (command.equals("getNewConnection")) {
    } else {
      // Unknown command
      return false;
    }
    if (!addListener(connectionId, requestId, callbackContext)) {
      return false;
    }
    sendIntent(i);
    return true;
  }

  private void sendIntent(final Intent intent) {
    // TODO: Switch to using bind() and direct method calls.
    if (!serviceIsUp) {
      pendingCommands.add(intent);
      return;
    }
    Context context = this.cordova.getActivity().getApplicationContext(); 
    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }

  private boolean addListener(final int connectionId, final String requestId, final CallbackContext callbackContext) {
    Pair<Integer, String> key = new Pair(connectionId, requestId);
    if (listeners.containsKey(key)) {
      // requestId is usually a UUID, but not for "onStateChange", so this could
      // happen if the user tries to set two such listeners on the same connection.
      return false;
    }

    listeners.put(key, callbackContext);
    return true;
  }
}
