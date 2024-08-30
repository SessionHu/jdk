/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

/*
 * @test
 * @summary test c1 to record type profile with CHA optimization
 * @requires (vm.opt.TieredStoAtLevel == null | vm.opt.TieredStopAtLevel == 4)
 * @library /test/lib
 * @run driver compiler.cha.TypeProfileFinalMethod
 */
package compiler.cha;

import java.io.File;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TypeProfileFinalMethod {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("Run")) {
            int cnt = 5300;
            // warmup test1 to be compiled with c1 and c2
            // and only compile test2 with c1
            for (int i = 0; i < cnt; i++) {
                test1(i);
            }
            for (int i = 0; i < cnt; i++) {
                test2(i);
            }
            Parent c = new Child2();
            System.out.println("======== break CHA");
            // trigger c2 to compile test2
            for (int i = 0; i < 100; i++) {
                test2(i);
            }
        } else {
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-Xbatch", "-XX:-UseOnStackReplacement",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:CompilerDirectivesFile="+System.getProperty("test.src", ".") + File.separator + "cha_control.txt",
                "-XX:Tier3InvocationThreshold=200", "-XX:Tier4InvocationThreshold=5000",
                "compiler.cha.TypeProfileFinalMethod", "Run");
            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            System.out.println("debug output");
            System.out.println(output.getOutput());
            System.out.println("debug output end");
            output.shouldHaveExitValue(0);
            output.shouldNotContain("failed to inline: virtual call");
            Pattern pattern = Pattern.compile("Child1::m.*  inline ");
            Matcher matcher = pattern.matcher(output.getOutput());
            int matchCnt = 0;
            while (matcher.find()) {
              matchCnt++;
            }
            Asserts.assertEquals(matchCnt, 2);  // inline Child1::m() twice
        }
    }

    static int test1(int i) {
        int ret = 0;
        Parent ix = getInstance();
        if (i<200) {
            return ix.m();
        }
        for (int j = 0; j < 50; j++) {
            ret += ix.m();     // the callsite we are interesting
        }
        return ret;
    }

    static int test2(int i) {
        return test1(i);
    }

    static Parent getInstance() {
        return new Child1();
    }
}

abstract class Parent {
    abstract public int m();
}

final class Child1 extends Parent {
    public int m() {
        return 1;
    }
}

final class Child2 extends Parent {
    public int m() {
        return 2;
    }
}
