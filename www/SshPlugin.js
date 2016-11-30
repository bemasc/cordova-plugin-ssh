var exec = require('cordova/exec');

// Either key or password must be specified
exports.connect = function(host, port, user, key, password) {
  return new Promise(function(F, R) {
    exec(F, R, "SshPlugin", "connect", [host, port, user, key, password]);
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

