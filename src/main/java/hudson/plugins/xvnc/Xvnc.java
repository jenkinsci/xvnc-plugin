package hudson.plugins.xvnc;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link BuildWrapper} that runs <tt>xvnc</tt>.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Xvnc extends BuildWrapper {

    /**
     * Whether or not to take a screenshot upon completion of the build.
     */
    public boolean takeScreenshot;
    
    private static final String FILENAME_SCREENSHOT = "screenshot.jpg";

    @DataBoundConstructor
    public Xvnc(boolean takeScreenshot) {
        this.takeScreenshot = takeScreenshot;
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final PrintStream logger = listener.getLogger();
        DescriptorImpl DESCRIPTOR = Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);

        // skip xvnc execution
        if (build.getBuiltOn().getAssignedLabels().contains(Label.get("noxvnc"))
                || build.getBuiltOn().getNodeProperties().get(NodePropertyImpl.class) != null) {
            return new Environment(){};
        }
        
        if (DESCRIPTOR.skipOnWindows && !launcher.isUnix()) {
            return new Environment(){};
        }
        
        if (DESCRIPTOR.cleanUp) {
            maybeCleanUp(launcher, listener);
        }

        String cmd = Util.nullify(DESCRIPTOR.xvnc);
        int baseDisplayNumber = DESCRIPTOR.baseDisplayNumber; 
        if (cmd == null) {
            cmd = "vncserver :$DISPLAY_NUMBER";
        }

        return doSetUp(build, launcher, logger, cmd, baseDisplayNumber, 3);
    }

    private Environment doSetUp(AbstractBuild build, final Launcher launcher, final PrintStream logger,
            String cmd, int baseDisplayNumber, int retries) throws IOException, InterruptedException {

        final int displayNumber = allocator.allocate(baseDisplayNumber);
        final String actualCmd = Util.replaceMacro(cmd, Collections.singletonMap("DISPLAY_NUMBER",String.valueOf(displayNumber)));

        logger.println(Messages.Xvnc_STARTING());

        String[] cmds = Util.tokenize(actualCmd);
        final Proc proc = launcher.launch().cmds(cmds).stdout(logger).pwd(build.getWorkspace()).start();
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
                if (retries > 0) {
                    return doSetUp(build, launcher, logger, cmd, baseDisplayNumber, retries - 1);
                } else {
                    throw new IOException(message);
                }
            }
        } else {
            vncserverCommand = null;
        }

        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                env.put("DISPLAY",":"+displayNumber);
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                if (takeScreenshot) {
                    FilePath ws = build.getWorkspace();
                    File artifactsDir = build.getArtifactsDir();
                    artifactsDir.mkdirs();
                    logger.println(Messages.Xvnc_TAKING_SCREENSHOT());
                    launcher.launch().cmds("import", "-window", "root", "-display", ":" + displayNumber, FILENAME_SCREENSHOT).
                            stdout(logger).pwd(ws).join();
                    ws.child(FILENAME_SCREENSHOT).copyTo(new FilePath(artifactsDir).child(FILENAME_SCREENSHOT));
                }
                logger.println(Messages.Xvnc_TERMINATING());
                if (vncserverCommand != null) {
                    // #173: stopping the wrapper script will accomplish nothing. It has already exited, in fact.
                    launcher.launch().cmds(vncserverCommand, "-kill", ":" + displayNumber).stdout(logger).join();
                } else {
                    // Assume it can be shut down by being killed.
                    proc.kill();
                }
                allocator.free(displayNumber);

                return true;
            }
        };
    }

    /**
     * Manages display numbers in use.
     */
    private static final DisplayAllocator allocator = new DisplayAllocator();

    /**
     * Whether {@link #maybeCleanUp} has already been run on a given node.
     */
    private static final Map<Node,Boolean> cleanedUpOn = new WeakHashMap<Node,Boolean>();
    
    // XXX I18N
    private static synchronized void maybeCleanUp(Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        Node node = Computer.currentComputer().getNode();
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
        public int baseDisplayNumber = 10;

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
}
