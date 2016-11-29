var exec = require('cordova/exec');

exports.connectWithPublicKey = function(host, port, user, key) {
  return new Promise(function(F, R) {
    exec(F, R, "SshPlugin", "connectWithPublicKey", [host, port, user, key]);
  });
};

exports.connectWithPassword = function(host, port, user, password) {
  return new Promise(function(F, R) {
    exec(F, R, "SshPlugin", "connectWithPassword", [host, port, user, password]);
  });
};

exports.disconnect = function() {
  return new Promise(function(F, R) {
    exec(F, R, "SshPlugin", "disconnect", []);
  });
}
exports.startProxy = function(port) {
  return new Promise(function(F, R) {
    exec(F, R, "SshPlugin", "startProxy", [port]);
  });
}

exports.stopProxy = function() {
  return new Promise(function(F, R) {
    exec(F, R, "SshPlugin", "stopProxy", []);
  });
}

