/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @bug 8339307
 * @requires vm.hasSA
 * @library /test/lib
 * @run driver TestJhsdbJstackUpcall
 */
public class TestJhsdbJstackUpcall {

    private static final int MAX_ITERATIONS = 20;

    /*
     * Test should focus JNI call (caller of upcall) because the frame
     * prior to the upcall cannot be obtained if some exception happens
     * in during to process upcall.
     */
    private static boolean isJNIFrame(List<String> lines) {
        return lines.stream()
                    .anyMatch(s -> s.startsWith(" - LingeredAppWithFFMUpcall.callJNI"));
    }

    private static void runJstackInLoop(LingeredApp app) throws Exception {
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            JDKToolLauncher launcher = JDKToolLauncher
                    .createUsingTestJDK("jhsdb");
            launcher.addVMArgs(Utils.getTestJavaOpts());
            launcher.addToolArg("jstack");
            launcher.addToolArg("--pid");
            launcher.addToolArg(Long.toString(app.getPid()));

            ProcessBuilder pb = SATestUtils.createProcessBuilder(launcher);
            Process jhsdb = pb.start();
            OutputAnalyzer out = new OutputAnalyzer(jhsdb);

            jhsdb.waitFor();

            System.out.println(out.getStdout());
            System.err.println(out.getStderr());

            out.shouldContain(LingeredAppWithFFMUpcall.THREAD_NAME);
            if (isJNIFrame(out.asLines())) {
                System.out.println("DEBUG: Test triggered interesting condition.");
                out.shouldNotContain("sun.jvm.hotspot.types.WrongTypeException");
                System.out.println("DEBUG: Test PASSED.");
                return; // If we've reached here, all is well.
            }
            System.out.println("DEBUG: Iteration: " + (i + 1)
                                 + " - Test didn't trigger interesting condition.");
            out.shouldNotContain("sun.jvm.hotspot.types.WrongTypeException");
        }
        throw new IllegalStateException("Could not find expected frame");
    }

    public static void main(String... args) throws Exception {
        SATestUtils.skipIfCannotAttach(); // throws SkippedException if attach not expected to work.
        LingeredApp app = null;

        try {
            // Needed for LingeredApp to be able to resolve native library.
            String libPath = System.getProperty("java.library.path");
            String[] vmArgs = (libPath != null)
                ? Utils.prependTestJavaOpts("-Djava.library.path=" + libPath)
                : Utils.getTestJavaOpts();

            app = new LingeredAppWithFFMUpcall();
            LingeredApp.startAppExactJvmOpts(app, vmArgs);
            System.out.println("Started LingeredApp with pid " + app.getPid());
            runJstackInLoop(app);
            System.out.println("Test Completed");
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        } finally {
            LingeredApp.stopApp(app);
        }
    }
}
