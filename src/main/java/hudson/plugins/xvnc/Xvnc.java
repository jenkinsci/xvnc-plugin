package hudson.plugins.xvnc;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.CheckForNull;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import jenkins.util.BuildListenerAdapter;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link BuildWrapper} that runs <tt>xvnc</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
public class Xvnc extends SimpleBuildWrapper {
    /**
     * Whether or not to take a screenshot upon completion of the build.
     */
    public boolean takeScreenshot;

    public Boolean useXauthority;

    private static final String FILENAME_SCREENSHOT = "screenshot.jpg";

    @DataBoundConstructor
    public Xvnc(boolean takeScreenshot, boolean useXauthority) {
        this.takeScreenshot = takeScreenshot;
        this.useXauthority = useXauthority;
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment)
            throws IOException, InterruptedException {
        final PrintStream logger = listener.getLogger();
        DescriptorImpl DESCRIPTOR = Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);

        // skip xvnc execution
        Computer c = workspace.toComputer();
        Node node = c != null ? c.getNode() : null;
        if (node == null) {
            throw new AbortException("No node recognized for " + workspace);
        }
        if (node.getAssignedLabels().contains(Jenkins.getInstance().getLabelAtom("noxvnc"))
                || node.getNodeProperties().get(NodePropertyImpl.class) != null) {
            return;
        }

        if (DESCRIPTOR.skipOnWindows && !launcher.isUnix()) {
            return;
        }

        if (DESCRIPTOR.cleanUp) {
            maybeCleanUp(launcher, listener, node);
        }

        String cmd = Util.nullify(DESCRIPTOR.xvnc);
        if (cmd == null) {
            cmd = "vncserver :$DISPLAY_NUMBER -localhost -nolisten tcp";
        }

        workspace.mkdirs();
        doSetUp(context, build, workspace, node, launcher, logger, cmd, 10, DESCRIPTOR.minDisplayNumber,
                DESCRIPTOR.maxDisplayNumber);
    }

    private void doSetUp(Context context, Run<?,?> build, FilePath workspace, Node node, final Launcher launcher, final PrintStream logger,
            String cmd, int retries, int minDisplayNumber, int maxDisplayNumber)
                    throws IOException, InterruptedException {

        final DisplayAllocator allocator = getAllocator(node);

        final int displayNumber = allocator.allocate(minDisplayNumber, maxDisplayNumber);
        final String actualCmd = Util.replaceMacro(cmd, Collections.singletonMap("DISPLAY_NUMBER",String.valueOf(displayNumber)));

        logger.println(Messages.Xvnc_STARTING());

        String[] cmds = Util.tokenize(actualCmd);

        final FilePath xauthority = workspace.createTempFile(".Xauthority-", "");
        final Map<String,String> xauthorityEnv = new HashMap<String, String>();
        if (useXauthority) {
            xauthorityEnv.put("XAUTHORITY", xauthority.getRemote());
            context.env("XAUTHORITY", xauthority.getRemote());
        } else {
            // Need something to identify it by for Launcher.kill in DisposerImpl.
            xauthorityEnv.put("XVNC_COOKIE", UUID.randomUUID().toString());
        }

        final Proc proc = launcher.launch().cmds(cmds).envs(xauthorityEnv).stdout(logger).pwd(workspace).start();
        final String vncserverCommand;
        if (cmds[0].endsWith("vncserver") && cmd.contains(":$DISPLAY_NUMBER")) {
            // Command just started the server; -kill will stop it.
            vncserverCommand = cmds[0];
            int exit = proc.join();
            if (exit != 0) {
                // XXX I18N
                String message = "Failed to run \'" + actualCmd + "\' (exit code " + exit + "), blacklisting display #" + displayNumber +
                        "; consider checking the \"Clean up before start\" option";
                // Do not release it; it may be "stuck" until cleaned up by an administrator.
                //allocator.free(displayNumber);
                allocator.blacklist(displayNumber);
                if (retries > 0) {
                    doSetUp(context, build, workspace, node, launcher, logger, cmd, retries - 1,
                            minDisplayNumber, maxDisplayNumber);
                    return;
                } else {
                    throw new IOException(message);
                }
            }
        } else {
            vncserverCommand = null;
        }

        context.env("DISPLAY", ":" + displayNumber);
        context.setDisposer(new DisposerImpl(displayNumber, xauthorityEnv, vncserverCommand, takeScreenshot, xauthority.getRemote()));
    }
    
    private static class DisposerImpl extends Disposer {
        
        private static final long serialVersionUID = 1;
        
        private final int displayNumber;
        private final Map<String,String> xauthorityEnv;
        private final @CheckForNull String vncserverCommand;
        private final boolean takeScreenshot;
        private final String xauthorityPath;

        DisposerImpl(int displayNumber, Map<String,String> xauthorityEnv, @CheckForNull String vncserverCommand, boolean takeScreenshot, String xauthorityPath) {
            this.displayNumber = displayNumber;
            this.xauthorityEnv = xauthorityEnv;
            this.vncserverCommand = vncserverCommand;
            this.takeScreenshot = takeScreenshot;
            this.xauthorityPath = xauthorityPath;
        }
        
        @Override public void tearDown(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            PrintStream logger = listener.getLogger();
            if (takeScreenshot) {
                logger.println(Messages.Xvnc_TAKING_SCREENSHOT());
                launcher.launch().cmds("echo", "$XAUTHORITY").envs(xauthorityEnv).stdout(logger).pwd(workspace).join();
                launcher.launch().cmds("ls", "-l", "$XAUTHORITY").envs(xauthorityEnv).stdout(logger).pwd(workspace).join();
                launcher.launch().cmds("import", "-window", "root", "-display", ":" + displayNumber, FILENAME_SCREENSHOT).
                        envs(xauthorityEnv).stdout(logger).pwd(workspace).join();
                build.getArtifactManager().archive(workspace, launcher, new BuildListenerAdapter(listener), Collections.singletonMap(FILENAME_SCREENSHOT, FILENAME_SCREENSHOT));
            }
            logger.println(Messages.Xvnc_TERMINATING());
            if (vncserverCommand != null) {
                // #173: stopping the wrapper script will accomplish nothing. It has already exited, in fact.
                launcher.launch().cmds(vncserverCommand, "-kill", ":" + displayNumber).envs(xauthorityEnv).stdout(logger).join();
            } else {
                // Assume it can be shut down by being killed.
                launcher.kill(xauthorityEnv);
            }
            Computer c = workspace.toComputer();
            Node node = c != null ? c.getNode() : null;
            if (node == null) {
                throw new AbortException("No node recognized for " + workspace);
            }
            getAllocator(node).free(displayNumber);
            workspace.child(xauthorityPath).delete();
        }
    }

    private static DisplayAllocator getAllocator(Node node) throws IOException {
        DisplayAllocator.Property property = node.getNodeProperties().get(DisplayAllocator.Property.class);
        if (property == null) {
            property = new DisplayAllocator.Property();
            node.getNodeProperties().add(property);
        }
        return property.getAllocator();
    }

    /**
     * Whether {@link #maybeCleanUp} has already been run on a given node.
     */
    private static final Map<Node,Boolean> cleanedUpOn = new WeakHashMap<Node,Boolean>();

    // XXX I18N
    private static synchronized void maybeCleanUp(Launcher launcher, TaskListener listener, Node node) throws IOException, InterruptedException {
        if (cleanedUpOn.put(node, true) != null) {
            return;
        }
        if (!launcher.isUnix()) {
            listener.error("Clean up not currently implemented for non-Unix nodes; skipping");
            return;
        }
        PrintStream logger = listener.getLogger();
        // ignore any error return codes
        launcher.launch().stdout(logger).cmds("pkill", "Xvnc").join();
        launcher.launch().stdout(logger).cmds("pkill", "Xrealvnc").join();
        launcher.launch().stdout(logger).cmds("sh", "-c", "rm -f /tmp/.X*-lock /tmp/.X11-unix/X*").join();
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        /**
         * xvnc command line. This can include macro.
         *
         * If null, the default will kick in.
         */
        public String xvnc;

        /*
         * Base X display number.
         */
        public int minDisplayNumber = 10;

        /*
         * Maximum X display number.
         */
        public int maxDisplayNumber = 99;

        /**
         * If true, skip xvnc launch on all Windows slaves.
         */
        public boolean skipOnWindows = true;

        /**
         * If true, try to clean up old processes and locks when first run.
         */
        public boolean cleanUp = false;

        public DescriptorImpl() {
            super(Xvnc.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.description();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            // XXX is this now the right style?
            req.bindJSON(this,json);
            save();
            return true;
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        public String getCommandline() {
            return xvnc;
        }

        public void setCommandline(String value) {
            this.xvnc = value;
        }

        public FormValidation doCheckCommandline(@QueryParameter String value) {
            if (Util.nullify(value) == null || value.contains("$DISPLAY_NUMBER")) {
                return FormValidation.ok();
            } else {
                return FormValidation.warningWithMarkup(Messages.Xvnc_SHOULD_INCLUDE_DISPLAY_NUMBER());
            }
        }
    }

    public Object readResolve() {
        if (useXauthority == null) useXauthority = true;
        return this;
    }
}
