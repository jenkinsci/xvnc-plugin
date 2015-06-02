/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import hudson.model.Node;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import java.util.Collections;
import org.jenkinsci.plugins.workflow.JenkinsRuleExt;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.CoreWrapperStep;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class XvncWorkflowTest {

    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void configRoundTrip() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                Xvnc xvnc = new Xvnc(true, false);
                CoreWrapperStep step = new CoreWrapperStep(xvnc);
                step = new StepConfigTester(story.j).configRoundTrip(step);
                story.j.assertEqualDataBoundBeans(xvnc, step.getDelegate());
            }
        });
    }

    @Test public void withDisplayAfterRestart() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                story.j.jenkins.addNode(new DumbSlave("slave", "dummy", tmp.newFolder("remoteFS").getPath(), "1", Node.Mode.NORMAL, "", story.j.createComputerLauncher(null), RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList())); // TODO JENKINS-26398 clumsy
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node('slave') {\n"
                        + "  wrap([$class: 'Xvnc']) {\n"
                        + "    semaphore 'withDisplayAfterRestart'\n"
                        + "    sh 'echo DISPLAY=$DISPLAY; which xmessage && xmessage -timeout 1 hello || :'\n"
                        + "  }\n"
                        + "}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("withDisplayAfterRestart/1", b);
            }
        });
        story.addStep(new Statement() {
            @SuppressWarnings("SleepWhileInLoop")
            @Override public void evaluate() throws Throwable {
                SemaphoreStep.success("withDisplayAfterRestart/1", null);
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                assertNotNull(p);
                WorkflowRun b = p.getBuildByNumber(1);
                assertNotNull(b);
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b));
                story.j.assertLogContains("DISPLAY=:", b);
            }
        });
    }

}
