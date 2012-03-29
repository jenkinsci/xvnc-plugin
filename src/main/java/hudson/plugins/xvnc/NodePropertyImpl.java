package hudson.plugins.xvnc;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Marker property to disable Xvnc execution on specific nodes.
 *
 * @author Kohsuke Kawaguchi
 */
public class NodePropertyImpl extends NodeProperty<Node> {
    @DataBoundConstructor
    public NodePropertyImpl() {}

    @Extension
    public static class DescriptorImpl extends NodePropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "Disable Xvnc execution on this node";
        }
    }
}
