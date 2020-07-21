/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.xvnc;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.xvnc.Xvnc.DescriptorImpl;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Builder;
import hudson.util.OneShotEvent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

public class XvncTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test @Bug(24773)
    public void distinctDisplaySpaceForSlaves() throws Exception {
        DumbSlave slaveA = j.createOnlineSlave();
        Node slaveB = j.jenkins;

        FreeStyleProject jobA = j.jenkins.createProject(FreeStyleProject.class, "jobA");
        jobA.setAssignedNode(slaveA);
        FreeStyleProject jobB = j.jenkins.createProject(FreeStyleProject.class, "jobB");
        jobB.setAssignedNode(slaveB);

        fakeXvncRun(jobA);
        fakeXvncRun(jobB);

        jobA.getBuildersList().add(new Blocker());
        Future<FreeStyleBuild> fb = jobA.scheduleBuild2(0);
        Blocker.RUNNING.block(1000);

        FreeStyleBuild build = jobB.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(build);

        Blocker.DONE.signal();
        j.assertBuildStatusSuccess(fb.get());
    }

    @Test // The number should not be allocated as builds are executed sequentially
    public void reuseDisplayNumberOnSameSlave() throws Exception {
        FreeStyleProject p = j.jenkins.createProject(FreeStyleProject.class, "project");

        runXvnc(p).cleanUp = true;

        j.buildAndAssertSuccess(p);
        j.buildAndAssertSuccess(p);
    }

    @Test
    public void takeScreenshot() throws Exception {
        FreeStyleProject p = j.jenkins.createProject(FreeStyleProject.class, "project");

        runXvnc(p, true, false).cleanUp = true;
        j.configRoundtrip(p);
        p = j.jenkins.getItemByFullName("project", FreeStyleProject.class);
        Xvnc wrapper = (Xvnc) p.getBuildWrappers().get(j.jenkins.getDescriptorByType(DescriptorImpl.class));

        assertTrue("Take screenshot should still be enabled.", wrapper.takeScreenshot);

        final FreeStyleBuild build = j.buildAndAssertSuccess(p);
        j.assertLogContains(Messages.Xvnc_TAKING_SCREENSHOT(), build);
    }

    @Test
    public void vncCommandVariable() throws Exception {
        FreeStyleProject p = j.jenkins.createProject(FreeStyleProject.class, "project");

        runXvnc(p, true, false).xvnc = "$VNC_COMMAND -nocursor :$DISPLAY_NUMBER";

        FreeStyleBuild build = j.buildAndAssertSuccess(p);
        j.assertLogContains(" -nocursor :", build);
    }

    @Test
    public void displayBlacklistedOnOneMachineShouldNotBeBlacklistedOnAnother() throws Exception {
        DumbSlave slaveA = j.createOnlineSlave();
        DumbSlave slaveB = j.createOnlineSlave();

        FreeStyleProject jobA = j.jenkins.createProject(FreeStyleProject.class, "jobA");
        jobA.setAssignedNode(slaveA);
        FreeStyleProject jobB = j.jenkins.createProject(FreeStyleProject.class, "jobB");
        jobB.setAssignedNode(slaveB);

        // blacklist :42 on slaveA
        DescriptorImpl descriptor = runXvnc(jobA);
        descriptor.xvnc = "vncserver-broken :$DISPLAY_NUMBER";
        descriptor.maxDisplayNumber = descriptor.minDisplayNumber = 42;
        j.assertBuildStatus(Result.FAILURE, jobA.scheduleBuild2(0).get());

        // use :42 on slaveB
        descriptor.cleanUp = true;
        descriptor.maxDisplayNumber = descriptor.minDisplayNumber = 42;
        j.buildAndAssertSuccess(jobB);
    }

    @Test
    public void blacklistedDisplayShouldStayBlacklistedBetweenBuilds() throws Exception {
        FreeStyleProject p = j.jenkins.createProject(FreeStyleProject.class, "project");

        // Blacklist
        runXvnc(p).xvnc = "vncserver-broken :$DISPLAY_NUMBER";
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

        // Should still fail
        runXvnc(p);
        FreeStyleBuild build = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

        @SuppressWarnings("deprecation")
        String log = build.getLog();
        assertTrue(log, log.contains("All available display numbers are allocated or blacklisted"));
    }

    @Test
    public void avoidNpeAfterDeserialiation() throws Exception {
        FreeStyleProject p = j.jenkins.createProject(FreeStyleProject.class, "project");

        runXvnc(p).cleanUp = true;
        j.buildAndAssertSuccess(p);

        j.jenkins.save();
        j.jenkins.reload();

        runXvnc(p).cleanUp = true;
        j.buildAndAssertSuccess(p);
    }

    @Test
    public void testXauthorityInWorkspace() throws Exception {
        assumeThat("java.io.tmpdir can't have spaces for this test to work properly",
                System.getProperty("java.io.tmpdir"), not(containsString(" ")));

        FreeStyleProject job = j.jenkins.createProject(FreeStyleProject.class, "jobA");
        job.setCustomWorkspace(tmp.newFolder().getAbsolutePath()); // Make sure workspace path have no spaces in it so it can be used for Xauthority

        runXvnc(job, true);

        job.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath[] list = build.getWorkspace().list(".Xauthority-*");
                assertEquals("There should be one Xauthority file in the workspace", 1, list.length);
                assertEquals(build.getWorkspace().getRemote(), list[0].getParent().getRemote());
                return true;
            }
        });
        j.buildAndAssertSuccess(job);

        FilePath[] list = job.getLastBuild().getWorkspace().list(".Xauthority-*");
        assertEquals("The Xauthority file should be removed", 0, list.length);
    }

    @Test
    public void testXauthorityInSlaveFsRoot() throws Exception {
        assumeThat("java.io.tmpdir can't have spaces for this test to work properly",
                System.getProperty("java.io.tmpdir"), not(containsString(" ")));
        File noSpaceFsRoot = tmp.newFolder();
        final DumbSlave slave = createTweakedSlave(noSpaceFsRoot);

        FreeStyleProject job = j.jenkins.createProject(FreeStyleProject.class, "job A");
        job.setAssignedNode(slave);

        runXvnc(job, true);

        job.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath[] list = slave.getRootPath().list(".Xauthority-*");
                assertEquals("There should be one Xauthority file in the slave fs root", 1, list.length);
                assertEquals(slave.getRootPath().getRemote(), list[0].getParent().getRemote());

                list = build.getWorkspace().list(".Xauthority-*");
                assertEquals("There should be no Xauthority file in the workspace", 0, list.length);

                return true;
            }
        });
        j.buildAndAssertSuccess(job);

        FilePath[] list = slave.getRootPath().list(".Xauthority-*");
        assertEquals("The Xauthority file should be removed", 0, list.length);
    }

    @Test
    public void testXauthorityInTemp() throws Exception {
        final File pathWithSpaces = tmp.newFolder("some bad path");
        final DumbSlave slave = createTweakedSlave(pathWithSpaces);
        final FilePath tmpPath = new FilePath(slave.getChannel(), slave.toComputer().getSystemProperties().get("java.io.tmpdir").toString());
        final FilePath[] initialFiles = tmpPath.list(".Xauthority-*");
        if (initialFiles.length > 0) {
            for (FilePath file : initialFiles) {
                file.delete();
            }
        }
        FreeStyleProject job = j.jenkins.createProject(FreeStyleProject.class, "job A");
        job.setAssignedNode(slave);

        runXvnc(job, true);

        job.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

                FilePath[] list = tmpPath.list(".Xauthority-*");
                assertEquals("There should be one Xauthority file in tmp", 1, list.length);
                assertEquals(tmpPath.getRemote(), list[0].getParent().getRemote());

                list = build.getWorkspace().list(".Xauthority-*");
                assertEquals("There should be no Xauthority file in the workspace", 0, list.length);

                list = slave.getRootPath().list(".Xauthority-*");
                assertEquals("There should be no Xauthority file in the slave fs root", 0, list.length);

                return true;
            }
        });
        j.buildAndAssertSuccess(job);

        FilePath[] list = tmpPath.list(".Xauthority-*");
        assertEquals("The Xauthority file should be removed", 0, list.length);
    }

    private DumbSlave createTweakedSlave(File fsRoot) throws IOException, Descriptor.FormException, URISyntaxException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        ComputerListener waiter = new ComputerListener() {
            @Override
            public void onOnline(Computer C, TaskListener t) {
                latch.countDown();
                unregister();
            }
        };
        waiter.register();
        //Create a slave manually
        final DumbSlave slave;
        synchronized (j.jenkins) {
            slave = new DumbSlave("SlaveX", "dummy",
                    fsRoot.getPath(), "1", Node.Mode.NORMAL, "", j.createComputerLauncher(null), RetentionStrategy.NOOP, Collections.EMPTY_LIST);
            j.jenkins.addNode(slave);

        }
        latch.await();
        return slave;
    }

    private Xvnc fakeXvncRun(FreeStyleProject p) throws Exception {
        final Xvnc xvnc = new Xvnc(false, false);
        p.getBuildWrappersList().add(xvnc);
        DescriptorImpl descriptor = Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
        descriptor.maxDisplayNumber = descriptor.minDisplayNumber = 42;
        // Do nothing so next build using the same display can succeed. This is poor man's simulation of distinct build machine
        descriptor.xvnc = "true";
        return xvnc;
    }

    private Xvnc.DescriptorImpl runXvnc(FreeStyleProject p) throws Exception {
        return runXvnc(p, false);
    }

    private Xvnc.DescriptorImpl runXvnc(FreeStyleProject p, boolean useXauthority) throws Exception {
        return runXvnc(p, false, useXauthority);
    }

    private Xvnc.DescriptorImpl runXvnc(FreeStyleProject p, boolean takeScreenShot, boolean useXauthority) throws Exception {
        final Xvnc xvnc = new Xvnc(takeScreenShot, useXauthority);
        p.getBuildWrappersList().add(xvnc);
        DescriptorImpl descriptor = j.jenkins.getDescriptorByType(DescriptorImpl.class);
        descriptor.maxDisplayNumber = descriptor.minDisplayNumber = new Random().nextInt(50) + 1;
        descriptor.xvnc = null;
        return descriptor;
    }

    private static class Blocker extends Builder {

        private static final OneShotEvent RUNNING = new OneShotEvent();
        private static final OneShotEvent DONE = new OneShotEvent();

        public Blocker() {}

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            RUNNING.signal();
            DONE.block(10000);
            return true;
        }
    }
}
