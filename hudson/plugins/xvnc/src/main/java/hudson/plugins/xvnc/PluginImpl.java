package hudson.plugins.xvnc;

import hudson.Plugin;
import hudson.tasks.BuildWrappers;

/**
 * Xvnc plugin.
 *
 * @plugin
 * @author Kohsuke Kawaguchi
 */
public class PluginImpl extends Plugin {
    public void start() throws Exception {
        BuildWrappers.WRAPPERS.add(Xvnc.DESCRIPTOR);
    }
}
