/*
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */
package com.ericsson.nms.security.taf.test.cases;

import static com.ericsson.nms.security.taf.test.operators.MySQLOperator.getDbNodes;
import static com.ericsson.nms.security.taf.test.operators.MySQLOperator.hostToString;
import static com.ericsson.oss.testware.security.generic.utility.Assertion.assertNotNull;
import static com.ericsson.oss.testware.security.generic.utility.Assertion.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.TafTestBase;
import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.tools.cli.TafCliToolShell;
import com.ericsson.cifwk.taf.tools.cli.TimeoutException;
import com.ericsson.de.tools.cli.CliCommandResult;
import com.ericsson.nms.security.taf.test.operators.MySQLOperator;
import com.google.inject.Inject;

public class MySQL_Functional_Test extends TafTestBase {

    private static final String SHOW_DATABASES_COMMAND = "/opt/mysql/bin/mysql -u root -p@@PASSWORD@@ -e \"show databases\"";
    private static final String SERVICE_MYSQL_STATUS_COMMAND = "service mysql status";
    private static final String MYSQL_PASSWORD_PREFIX = "MySQL password:";
    private static final String GLOBAL_PROPERTIES_PATH = "/ericsson/tor/data/global.properties";
    private static final String GET_PASSWORD_COMMAND = "echo " + MYSQL_PASSWORD_PREFIX + " `echo $(cat " + GLOBAL_PROPERTIES_PATH
            + " | grep idm_mysql_admin_password | awk -F= '{ i = index($0,\"=\"); print substr($0,i+1)}')"
            + " | openssl enc -a -d -aes-128-cbc -salt -kfile /ericsson/tor/data/idenmgmt/idmmysql_passkey`";
    private static final Logger LOGGER = LoggerFactory.getLogger(MySQL_Functional_Test.class);
    
    @Inject
    MySQLOperator operator;

    private String pwdValue;

    @BeforeMethod
    public void initializeAndRunPreCheck() {
        operator.checkConnectionsAndReconnectIfNecessary();
        operator.useDbWithActiveMySQL();
    }

    /**
     * @DESCRIPTION Initialize password variables using testing server's global properties file
     * @PRE Connection to SUT
     * @PRIORITY HIGH
     * @VUsers 1
     * @throws TimeoutException
     */
    @TestId(id = "TORF-20202_Func_2", title = "Initialize variables using testing server's global properties file")
    @Test(groups = { "Acceptance" })
    public void initializeTestingVariables() {

        LOGGER.debug("A command to execute: "  + GET_PASSWORD_COMMAND);
 //       setTestDebug("A command to execute: " + GET_PASSWORD_COMMAND);
        CliCommandResult result = operator.getShell().execute(GET_PASSWORD_COMMAND);
        pwdValue = getPasswordFromStdout(result.getOutput());
        LOGGER.info("The encrypted password equals: "  + pwdValue);

        assertNotNull("The encrypted password is null.", pwdValue);
        //assertThat("The encrypted password is null.", pwdValue, is(notNullValue()));
        assertTrue("The encrypted password is empty.", pwdValue.length() > 0);
        //assertThat("The encrypted password is empty.", pwdValue.length(), is(greaterThan(0)));
    }

    /**
     * @DESCRIPTION Verify MySQL Status
     * @PRE Connection to SUT
     * @VUsers 1
     * @PRIORITY HIGH
     * @throws TimeoutException
     */
    @TestId(id = "TORF-9565_Func_1", title = "Verify MySQL status")
    @Test(groups = { "Acceptance" })
    public void verifyMySQlStatus() throws InterruptedException, TimeoutException {
        int combinedStatus = 0;

        for (final Host host : getDbNodes()) {
            final int status = getMySQLStatus(operator.use(host).getShell());

            LOGGER.info("The " + hostToString(host) + " MySQL status returns: " + status);
            combinedStatus += (status == 0 ? 1 : 0);
        }

        assertTrue("No MySQL instances are running", combinedStatus > 0);
        //assertThat("No MySQL instances are running", combinedStatus, is(greaterThan(0)));
        assertTrue("Exactly one MySQL should be running", combinedStatus == 1);
        //assertThat("Exactly one MySQL should be running", combinedStatus, is(equalTo(1)));
    }

    /**
     * @DESCRIPTION Verify MySQL DB status
     * @PRE Connection to SUT
     * @VUsers 1
     * @PRIORITY HIGH
     * @throws TimeoutException
     */
    @TestId(id = "TORE-9565_Func_2", title = "Verify MySQL DB status")
    @Test(groups = { "Acceptance" })
    public void verifyMySQLDB() {
        final String showDatabaseCommand = SHOW_DATABASES_COMMAND.replace("@@PASSWORD@@", pwdValue);
        LOGGER.debug("A command to execute: " + showDatabaseCommand);
        //setTestDebug("A command to execute: " + showDatabaseCommand);
        CliCommandResult result = operator.getShell().execute(showDatabaseCommand,30);

        final String stdOut = result.getOutput();
        final int commandExitValue = result.getExitCode();
        LOGGER.info("The command: " + showDatabaseCommand + " exit value: " + commandExitValue);
        LOGGER.debug("The returned output: " + stdOut);
        //setTestDebug("The returned output: " + stdOut);

        assertTrue("The exit value should be 0", commandExitValue == 0);
        //assertThat("The exit value should be 0", commandExitValue, is(0));
        assertTrue("OpenIDM database does not exist", stdOut.contains("openidm"));
        //assertThat("OpenIDM database does not exist", stdOut, containsString("openidm"));
    }

    private int getMySQLStatus(final TafCliToolShell shell) {
    	CliCommandResult result = shell.execute(SERVICE_MYSQL_STATUS_COMMAND,30);

        LOGGER.debug("A command to execute: " + SERVICE_MYSQL_STATUS_COMMAND);

        final String stdOut = result.getOutput();
        final int commandExitValue = result.getExitCode();
        LOGGER.debug("MySQL status output: " + stdOut);
        LOGGER.info("The command: " + SERVICE_MYSQL_STATUS_COMMAND + " exit value: " + commandExitValue);

        return commandExitValue;
    }

    private String getPasswordFromStdout(final String stdOut) {
        final String[] lines = stdOut.split(System.getProperty("line.separator"));
        for (final String line : lines) {
            if (line.contains(MYSQL_PASSWORD_PREFIX)) {
                return line.split(" ")[2];
            }
        }
        return null;
    }
}
