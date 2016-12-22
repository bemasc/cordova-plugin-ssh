var exec = require('cordova/exec');

function getNewConnection_() {
  return new Promise(function(F, R) {
    exec(F, R, "SshPlugin", "getNewConnection", []);
  });
}

function Connection(id) {
  if (typeof id === 'number') {
    this.id_ = Promise.resolve(id);
  } else {
    this.id_ = getNewConnection_();
  }
}

Connection.State = {
  INACTIVE: 0,
  CONNECTED: 1,
  PROXYING: 2,
  DISCONNECTED: 3
};

Connection.getAll = function() {
  return new Promise(function(F, R) {
    exec(F, R, "SshPlugin", "getIds", []);
  }).then(function(ids) {
    return JSON.parse(ids).map(function(id) {
      return new Connection(id);
    });
  });
};

Connection.prototype.getId = function() {
  return this.id_;
};

Connection.prototype.exec_ = function(cmd, args) {
  return this.id_.then(function(id) {
    return new Promise(function(F, R) {
      exec(F, R, "SshPlugin", cmd, [id].concat(args));
    }).then(function(result) {
      if (result) {
        return JSON.parse(result);
      }
    });
  }.bind(this));
};

// Either key or password must be specified
Connection.prototype.connect = function(host, port, user, key, password) {
  return this.exec_("connect", [host, port, user, key, password]);
};

Connection.prototype.disconnect = function() {
  return this.exec_("disconnect", []);
};

Connection.prototype.startProxy = function(port) {
  return this.exec_("startProxy", [port || 0]);
};

Connection.prototype.stopProxy = function() {
  return this.exec_("stopProxy", []);
};

Connection.prototype.getConnectionInfo = function() {
  return this.exec_("getInfo", []);
};

Connection.prototype.onStateChange = function(listener) {
  var onFail = function(fail) {
    console.warn("Got failed state change event: " + fail);
  };
  this.id_.then(function(id) {
    exec(listener, onFail, "SshPlugin", "onStateChange", [id]);
  });
};

exports.Connection = Connection;
