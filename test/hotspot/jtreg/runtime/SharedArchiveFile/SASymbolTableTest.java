/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test SASymbolTableTest
 * @requires vm.cds
 * @summary Walk symbol table using SA, with and without CDS.
 * @requires (vm.opt.UseCompressedOops == null) | (vm.opt.UseCompressedOops == true)
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          jdk.hotspot.agent/sun.jvm.hotspot.oops
 *          jdk.hotspot.agent/sun.jvm.hotspot.memory
 *          jdk.hotspot.agent/sun.jvm.hotspot.runtime
 *          jdk.hotspot.agent/sun.jvm.hotspot.tools
 *          java.management
 * @build SASymbolTableTestAgent
 * @run main SASymbolTableTest
 */

import java.util.Arrays;
import java.util.List;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.apps.LingeredApp;

/*
 * The purpose of this test is to validate that we can use SA to
 * attach a process and walk its SymbolTable, regardless whether
 * the attachee process runs in CDS mode or not.
 *
 * SASymbolTableTest Just sets up the agent and attachee processes.
 * The SymbolTable walking is done in the SASymbolTableTestAgent class.
 */
public class SASymbolTableTest {
    static String jsaName = "./SASymbolTableTest.jsa";
    private static LingeredApp theApp = null;


    public static void main(String[] args) throws Exception {
        if (!Platform.shouldSAAttach()) {
            System.out.println("SA attach not expected to work - test skipped.");
            return;
        }

        CDSTestUtils.createArchiveAndCheck();
        run(true);
        run(false);
    }


    private static void run(boolean useArchive) throws Exception {
        String flag = useArchive ? "auto" : "off";

        try {
            // (1) Launch the attachee process
            System.out.println("Starting LingeredApp");
            List<String> vmOpts = Arrays.asList(
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:SharedArchiveFile=" + jsaName,
                    "-Xshare:" + flag,
                    "-showversion");                // so we can see "sharing" in the output

            theApp = LingeredApp.startApp(vmOpts);

            // (2) Launch the agent process
            long pid = theApp.getPid();
            System.out.println("Attaching agent to " + pid );
            ProcessBuilder tool = ProcessTools.createJavaProcessBuilder(
                    "--add-modules=jdk.hotspot.agent",
                    "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.oops=ALL-UNNAMED",
                    "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.memory=ALL-UNNAMED",
                    "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.runtime=ALL-UNNAMED",
                    "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.tools=ALL-UNNAMED",
                    "SASymbolTableTestAgent",
                    Long.toString(pid));
            OutputAnalyzer output = CDSTestUtils.executeAndLog(tool, "tool");
            if (output.getStdout().contains("connected too early")) {
                System.out.println("SymbolTable not created by VM - test skipped");
                return;
            }
            output.shouldHaveExitValue(0);
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }
}
