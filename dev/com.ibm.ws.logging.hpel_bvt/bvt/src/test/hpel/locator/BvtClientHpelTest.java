package test.hpel.locator;

/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import test.common.SharedLocationManager;
import test.common.SharedOutputManager;

import com.ibm.wsspi.kernel.service.location.WsLocationAdmin;
import com.ibm.wsspi.kernel.service.location.WsResource;

/**
 *
 */
public class BvtClientHpelTest {
    private static SharedOutputManager outputMgr;

    /**
     * Capture stdout/stderr output to the manager.
     * 
     * @throws Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // There are variations of this constructor: 
        // e.g. to specify a log location or an enabled trace spec. Ctrl-Space for suggestions
        outputMgr = SharedOutputManager.getInstance();
        outputMgr.logTo("../com.ibm.ws.logging.hpel_test/build/bvt-logs");
        outputMgr.captureStreams();
    }

    /**
     * Final teardown work when class is exiting.
     * 
     * @throws Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        // Make stdout and stderr "normal"
        outputMgr.restoreStreams();
    }

    /**
     * Individual teardown after each test.
     * 
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        // Clear the output generated after each method invocation
        outputMgr.resetStreams();
    }

    // List of the files we should find during normal server start up
    // The only files in logs should be state, logdata, tracedata, and console.log
    final static String expectedConsole = "console.*\\.log";
    final static String[] expected = { "state/",
                                      "logdata/",
                                      "tracedata/",
                                      expectedConsole
    };
    // Mask indicating that all expected files were found.
    final static int expectedMask = (1 << expected.length) - 1;

    // TextLog pattern.
    final static String expectedText = "TextLog_.*\\.log";

    /**
     * Test configuration values.
     */
    @Test
    public void clientSideMethod() {
        final String m = "clientSideMethod";
        WsResource server_xml = null;
        WsResource server_backup = null;
        try {
            // Do stuff here. The outputMgr catches all output issued to stdout or stderr
            // unless/until an unexpected exception occurs. failWithThrowable will copy
            // all captured output back to the original streams before failing
            // the testcase.

            // Use the shared location manager (in concert with the WsLocationAdmin service)
            // to find the BVT server's location in the image (you could then check
            // for the presence/absence of files generated by the server-side test).
            WsLocationAdmin admin = (WsLocationAdmin) SharedLocationManager.createImageLocations("build.hpel.bvt");

            WsResource logs = admin.getServerResource("logs/");

            // List of unexpected files
            List<String> unexpected = new ArrayList<String>();
            // Mask indicating what expected files were found.
            int matchMask = 0;
            for (Iterator<String> it = logs.getChildren(); it.hasNext();) {
                String actual = it.next();
                boolean found = false;
                for (int i = 0; i < expected.length; i++) {
                    if (actual.matches(expected[i])) {
                        matchMask |= 1 << i; // Set 'found expected file' bit.
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    unexpected.add(actual);
                }
            }
            StringBuilder sb = new StringBuilder();
            if (matchMask != expectedMask) {
                sb.append("Missing expected files in server's logs:");
                for (int i = 0; i < expected.length; i++) {
                    if ((matchMask & (1 << i)) == 0) {
                        sb.append("\"").append(expected[i]).append("\" ");
                    }
                }
            }
            if (unexpected.size() > 0) {
                sb.append("Unexpected files in server's logs: ");
                for (String file : unexpected) {
                    sb.append("\"").append(file).append("\" ");
                }
            }
            if (sb.length() > 0) {
                fail(sb.toString());
            }

            // Find console.log file to monitor for updates
            String consolePath = null;
            for (Iterator<String> it = logs.getChildren(expectedConsole); it.hasNext();) {
                if (consolePath == null) {
                    consolePath = it.next();
                } else {
                    fail("Found more than one console file: " + consolePath + " and " + it.next());
                }
            }
            assertNotNull("console.log is not present in server's logs directory", consolePath);
            File consoleFile = logs.getChild(consolePath).asFile();

            // Enable TextLog and see that TextLog appeared among files as well.
            server_xml = admin.getServerResource("server.xml");
            // Create server.xml backup
            server_backup = admin.getServerResource("server.xml-backup");
            server_backup.put(server_xml.get());
            // Override with server.xml enabling TextLog
            WsResource serverWithTextLog_xml = admin.getServerResource("server-withTextLog.xml");
            server_xml.put(serverWithTextLog_xml.get());

            // Wait for change to be processed
            boolean foundUpdate = false;
            StringBuilder consoleText = null;
            for (int i = 0; i < 30; i++) {
                Thread.sleep(1000);
                consoleText = getTextFileContent(consoleFile);
                //[AUDIT   ] CWWKG0017I: The server configuration was successfully updated in 0.046 seconds.

                if (consoleText.indexOf("CWWKG0017I") != -1) {
                    foundUpdate = true;
                    break;
                }
            }
            assertTrue("Didn't find configuration update message in console.log after waiting for 30 seconds", foundUpdate);

            // Verify that TextLog file appeared and there's still no unexpected files.
            unexpected.clear();
            boolean foundText = false;
            for (Iterator<String> it = logs.getChildren(); it.hasNext();) {
                String actual = it.next();
                boolean found = actual.matches(expectedText);
                if (found) {
                    foundText = true;
                    continue;
                }
                for (int i = 0; i < expected.length; i++) {
                    if (actual.matches(expected[i])) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    unexpected.add(actual);
                }
            }

            assertTrue("TextLog file was unexpectedly found when text copy was enabled in server.xml", !foundText);
            //assertTrue("TextLog file was not found when text copy was enabled in server.xml", foundText);

            if (unexpected.size() > 0) {
                sb.append("Unexpected files in server's logs after text copy was enabled: ");
                for (String file : unexpected) {
                    sb.append("\"").append(file).append("\" ");
                }
                fail(sb.toString());
            }

        } catch (Throwable t) {
            outputMgr.failWithThrowable(m, t);
        } finally {
            if (server_backup != null) {
                // Restore server.xml from backup copy
                try {
                    server_xml.put(server_backup.get());
                } catch (IOException e) {
                    // Ignore exceptions in this cleanup code.
                }

            }
        }
    }

    public static StringBuilder getTextFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            br.close();
        }
        return sb;
    }

}