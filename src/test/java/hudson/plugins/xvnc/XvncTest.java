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
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.plugins.xvnc.Xvnc.DescriptorImpl;
import hudson.slaves.DumbSlave;
import hudson.tasks.Builder;
import hudson.util.OneShotEvent;

import java.io.IOException;
import java.util.concurrent.Future;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

public class XvncTest {

    @Rule public JenkinsRule j = new JenkinsRule();

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
    public void displayBlacklistedOnOneMachineShouldNotBeBlacklistedOnAnother() throws Exception {
        DumbSlave slaveA = j.createOnlineSlave();
        DumbSlave slaveB = j.createOnlineSlave();

        FreeStyleProject jobA = j.jenkins.createProject(FreeStyleProject.class, "jobA");
        jobA.setAssignedNode(slaveA);
        FreeStyleProject jobB = j.jenkins.createProject(FreeStyleProject.class, "jobB");
        jobB.setAssignedNode(slaveB);

        // blacklist :42 on slaveA
        runXvnc(jobA).xvnc = "vncserver-broken :$DISPLAY_NUMBER";
        j.assertBuildStatus(Result.FAILURE, jobA.scheduleBuild2(0).get());

        // use :42 on slaveB
        runXvnc(jobB).cleanUp = true;
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
    public void testXauthorityInFsRootFalse() throws Exception {
        DumbSlave slaveA = j.createOnlineSlave();

        FreeStyleProject jobA = j.jenkins.createProject(FreeStyleProject.class, "jobA");
        jobA.setAssignedNode(slaveA);
        runXvnc(jobA, true).xauthorityInFsRoot = false;

        jobA.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath[] list = build.getWorkspace().list(".Xauthority-*");
                assertEquals("There should be one Xauthority file", 1, list.length);
                assertEquals(build.getWorkspace().getRemote(), list[0].getParent().getRemote());
                return true;
            }
        });
        j.buildAndAssertSuccess(jobA);

        FilePath[] list = jobA.getLastBuild().getWorkspace().list(".Xauthority-*");
        assertEquals("The Xauthority file should be removed", 0, list.length);
    }

    @Test
    public void testXauthorityInFsRootTrue() throws Exception {
        DumbSlave slaveA = j.createOnlineSlave();

        FreeStyleProject jobA = j.jenkins.createProject(FreeStyleProject.class, "jobA");
        jobA.setAssignedNode(slaveA);
        runXvnc(jobA, true).xauthorityInFsRoot = true;

        jobA.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath rootPath = build.getWorkspace().toComputer().getNode().getRootPath();
                FilePath[] list = rootPath.list(".Xauthority-*");
                assertEquals("There should be one Xauthority file", 1, list.length);
                assertEquals(rootPath.getRemote(), list[0].getParent().getRemote());

                list = build.getWorkspace().list(".Xauthority-*");
                assertEquals("There should be no Xauthority files in the workspace", 0, list.length);

                return true;
            }
        });
        j.buildAndAssertSuccess(jobA);

        FilePath[] list = slaveA.getRootPath().list(".Xauthority-*");
        assertEquals("The Xauthority file should be removed", 0, list.length);
    }

    @Test
    public void testGlobalConfigRoundtrip() throws Exception {
        DescriptorImpl descriptor = j.jenkins.getDescriptorByType(DescriptorImpl.class);
        descriptor.maxDisplayNumber = descriptor.minDisplayNumber = 42;
        descriptor.xvnc = "true";
        descriptor.xauthorityInFsRoot = true;
        descriptor.skipOnWindows = true;

        j.configRoundtrip();

        descriptor = j.jenkins.getDescriptorByType(DescriptorImpl.class);

        assertEquals(42, descriptor.maxDisplayNumber);
        assertEquals("true", descriptor.xvnc);
        assertTrue(descriptor.xauthorityInFsRoot);
        assertTrue(descriptor.skipOnWindows);
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
        final Xvnc xvnc = new Xvnc(false, useXauthority);
        p.getBuildWrappersList().add(xvnc);
        DescriptorImpl descriptor = Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
        descriptor.maxDisplayNumber = descriptor.minDisplayNumber = 42;
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
