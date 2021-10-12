# XVnc plugin for Jenkins

This plugin lets you run an Xvnc session during a build. This is handy if your build includes UI testing that needs a display available.

Each build using the plugin gets its own display allocated from a free list, by default starting with :10 and ending with :99.
(The `$DISPLAY` environment variable is set for the build by the plugin.)
Thus you can freely run builds on multiple executors without fear of interference.

If there is some problem starting a display server with a given number, that number will be blacklisted
for the remainder of the Jenkins session and the plugin will try ten more times before giving up.
This is commonly due to stale locks that did not get cleaned up properly.
There is also an option to clean up locks when starting the first Xvnc-enabled build in a given session.
You can record your vnc session with [VncRecorder Plugin](https://plugins.jenkins.io/vncrecorder/).

Note: you must have started the vncserver at least one time before you use it with the plugin. This is to create a password. Otherwise Jenkins fails.

If you are running Windows you probably do not want this plugin.
