package hudson.plugins.xvnc;

import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.BuildWrapper;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Map;

/**
 * {@link BuildWrapper} that runs <tt>xvnc</tt>.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Xvnc extends BuildWrapper {
    public Environment setUp(Build build, Launcher launcher, BuildListener listener) throws IOException {
        final PrintStream logger = listener.getLogger();

        String cmd = Util.nullify(DESCRIPTOR.xvnc);
        if(cmd==null)
            cmd = "vncserver :$DISPLAY_NUMBER";

        final int displayNumber = allocator.allocate();
        cmd = Util.replaceMacro(cmd, Collections.singletonMap("DISPLAY_NUMBER",String.valueOf(displayNumber)));

        logger.println("Starting xvnc");

        final Proc proc = launcher.launch(cmd, new String[0], logger, build.getProject().getWorkspace());

        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                env.put("DISPLAY",":"+displayNumber);
            }

            public boolean tearDown(Build build, BuildListener listener) throws IOException {
                logger.println("Terminating xvnc");
                proc.kill();

                return true;
            }
        };
    }

    public Descriptor<BuildWrapper> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Manages display numbers in use.
     */
    private static final DisplayAllocator allocator = new DisplayAllocator();
    
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<BuildWrapper> {
        /**
         * xvnc command line. This can include macro.
         *
         * If null, the default will kick in.
         */
        public String xvnc;

        DescriptorImpl() {
            super(Xvnc.class);
        }

        public String getDisplayName() {
            return "Run Xvnc during build";
        }

        public boolean configure(StaplerRequest req) throws FormException {
            req.bindParameters(this,"xvnc.");
            return true;
        }

        public Xvnc newInstance(StaplerRequest req) throws FormException {
            return new Xvnc();
        }
    }
}
