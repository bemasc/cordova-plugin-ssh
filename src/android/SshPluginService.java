package org.uproxy.cordovasshplugin;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.SortedMap;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.uproxy.cordovasshplugin.SshWrapper;

public class SshPluginService extends Service {
  private static final String LOG_TAG = "SshPluginService";

  private SortedMap<Integer, SshWrapper> connections = new TreeMap();

  @Override
  public IBinder onBind(Intent intent) {
    // TODO: Return a custom binder that provides direct access to this.
    return new Binder();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    Log.i(LOG_TAG, "destroy");
  }

  private BroadcastReceiver messageReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          int connectionId = intent.getIntExtra("connectionId", -1);
          String requestId = intent.getStringExtra("requestId");
          String command = intent.getStringExtra("command");
          if ("getNewConnection".equals(command)) {
            fulfill(intent, getNewConnection());
          } else if ("getIds".equals(command)) {
            fulfill(intent, getIds());
          } else {
            if (connections.containsKey(connectionId)) {
              connections.get(connectionId).execute(intent);
            } else {
              reject(intent, "No such connection");
            }
          }
        }
      };

  @Override
  public void onCreate() {
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(messageReceiver, new IntentFilter("toService"));
  }

  public void fulfill(Intent request, JSONObject payload) {
    reply(request, payload.toString(), true);
  }

  public void fulfill(Intent request, JSONArray payload) {
    reply(request, payload.toString(), true);
  }

  public void fulfill(Intent request, String payload) {
    reply(request, JSONObject.quote(payload), true);
  }

  public void fulfill(Intent request, Number payload) {
    try {
      reply(request, JSONObject.numberToString(payload), true);
    } catch (JSONException e) {
      reject(request, e.toString());
    }
  }

  public void fulfill(Intent request) {
    reply(request, null, true);
  }

  public void reject(Intent request, String message) {
    Log.w(LOG_TAG, "Rejecting: " + message);
    reply(request, message, false);
  }

  private void reply(Intent request, String payload, boolean success) {
    Intent reply = new Intent("fromService");
    reply.putExtra("connectionId", request.getIntExtra("connectionId", -1));
    reply.putExtra("requestId", request.getStringExtra("requestId"));
    reply.putExtra("command", request.getStringExtra("command"));
    reply.putExtra("success", success);
    if (payload != null) {
      reply.putExtra("payload", payload);
    }
    LocalBroadcastManager.getInstance(this).sendBroadcast(reply);
  }

  public void emit(int connectionId, String requestId, int payload) {
    Intent fakeRequest = new Intent("toService");
    fakeRequest.putExtra("connectionId", connectionId);
    fakeRequest.putExtra("requestId", requestId);
    fulfill(fakeRequest, payload);
  }

  private int getNewConnection() {
    int newId = connections.isEmpty() ? 0 : connections.lastKey() + 1;
    connections.put(newId, new SshWrapper(this, newId));
    return newId;
  }

  private JSONArray getIds() {
    return new JSONArray(connections.keySet());
  }

  public void remove(int connectionId) {
    connections.remove(connectionId);
  }
}
