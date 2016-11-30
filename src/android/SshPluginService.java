package org.uproxy.cordovasshplugin;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.DynamicPortForwarder;

public class SshPluginService extends Service {
  private static Connection connection = null;
  private static DynamicPortForwarder proxy = null;

  private static final String LOG_TAG = "SshPluginService";

  @Override
  public IBinder onBind(Intent intent) {
    return null;  // No need to support binding this service.
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(LOG_TAG, "start command");
    String host = intent.getStringExtra("host");
    int port = intent.getIntExtra("port", -1);
    String user = intent.getStringExtra("user");
    String key = intent.getStringExtra("key");
    String password = intent.getStringExtra("password");
    connect(host, port, user, key, password);

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    Log.i(LOG_TAG, "destroy");
    try {
      if (proxy != null) {
        proxy.close();
      }
      if (connection != null) {
        // connection.close() does network operations so it has to happen off-thread.
        disconnect();
      }
    } catch (IOException e) {
      Log.e(LOG_TAG, e.toString());
    }
  }

  private BroadcastReceiver messageReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if ("startProxy".equals(intent.getAction())) {
            int port = intent.getIntExtra("port", -1);
            startProxy(port);
          } else if ("stopProxy".equals(intent.getAction())) {
            stopProxy();
          }
        }
      };

  @Override
  public void onCreate() {
    IntentFilter broadcastFilter = new IntentFilter("startProxy");
    broadcastFilter.addAction("stopProxy");
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(messageReceiver, broadcastFilter);
  }

    private void connect(final String host, final int port, final String user, final String key, final String password) {
      final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
      connection = new Connection(host, port);
      new Thread(
          new Runnable() {
              public void run() {
                  boolean success = false;
                  if (connection == null) {
                    Log.e(LOG_TAG, "Connection disappeared!");
                  } else {
                    String nonNullPassword = "";
                    if (password != null) {
                      nonNullPassword = password;
                    }
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
                    }
                  }

                  Intent connectBroadcast = new Intent("connectResult");
                  connectBroadcast.putExtra("success", success);
                  broadcastManager.sendBroadcast(connectBroadcast);
              }
            }, "Initiate connection")
        .start();
    }

    private void disconnect() {
      // connection.close() does network operations so it has to happen off-thread.
      new Thread(
          new Runnable() {
              public void run() {
                  connection.close();
                  connection = null;
              }
            }, "Disconnect")
        .start();
    }

    private void startProxy(final int port) {
      boolean success = false;
      try {
        proxy = connection.createDynamicPortForwarder(port);
        success = true;
      } catch (IOException e) {
        Log.e(LOG_TAG, e.toString());
      }
      Intent connectBroadcast = new Intent("startProxyResult");
      connectBroadcast.putExtra("success", success);
      LocalBroadcastManager.getInstance(this).sendBroadcast(connectBroadcast);
    }

    private void stopProxy() {
      boolean success = false;
      if (proxy == null) {
        Log.e(LOG_TAG, "No proxy to stop");
      } else {
        try {
          proxy.close();
          proxy = null;
          success = true;
        } catch (IOException e) {
          Log.e(LOG_TAG, e.toString());
        }
      }
      Intent connectBroadcast = new Intent("stopProxyResult");
      connectBroadcast.putExtra("success", success);
      LocalBroadcastManager.getInstance(this).sendBroadcast(connectBroadcast);
    }
}
