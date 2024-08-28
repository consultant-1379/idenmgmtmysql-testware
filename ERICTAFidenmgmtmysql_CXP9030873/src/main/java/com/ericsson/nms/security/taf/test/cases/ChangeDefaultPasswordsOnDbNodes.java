/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.nms.security.taf.test.cases;

import static com.ericsson.nms.security.taf.test.operators.MySQLOperator.getDbNodes;
import static com.ericsson.nms.security.taf.test.operators.MySQLOperator.hostToString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.TorTestCaseHelper;
import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.User;
import com.ericsson.cifwk.taf.tools.cli.TafCliToolShell;
import com.ericsson.cifwk.taf.tools.cli.TafCliTools;
import com.ericsson.cifwk.taf.tools.cli.TimeoutException;
import com.ericsson.cifwk.taf.tools.cli.jsch.JSchCLIToolException;
import com.ericsson.de.tools.cli.WaitConditions;
import com.ericsson.nms.security.taf.test.operators.MySQLOperator;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

public class ChangeDefaultPasswordsOnDbNodes extends TorTestCaseHelper {

    private static final String OLD_LITP_ADMIN_PASSWORD = "passw0rd";
    private static final String OLD_ROOT_PASSWORD = "litpc0b6lEr";
    private static final String NEW_PASSWORD = MySQLOperator.PASSWORD;
    private static final User LITP_ADMIN = MySQLOperator.USER_LITP_ADMIN;
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeDefaultPasswordsOnDbNodes.class);

    private String response_command;

    @TestId(id = "TORF-54035_Func_1", title = "Change default passwords on db nodes and verify access to db nodes aaa")
    @Test
    public void changePasswordsAndVerifyAccessToDbNodes() {
        LOGGER.info("Checking passwords");
        for (final Host host : getDbNodes()) {
            LOGGER.info("Check passwords on the DB node: {}", hostToString(host));

            if (isLitpAdminPasswordIncorrect(host)) {
                changeLitpAdminPassword(host, OLD_LITP_ADMIN_PASSWORD, NEW_PASSWORD);
            }

            if (isRootPasswordIncorrect(host)) {
                changeRootPassword(host, OLD_ROOT_PASSWORD, NEW_PASSWORD);
            }
        }
    }

    private boolean isLitpAdminPasswordIncorrect(final Host host) {
        boolean state = true;
        LOGGER.info("Trying to access {} as litp-admin user.", hostToString(host));
        TafCliToolShell shell = TafCliTools.sshShell(HostConfigurator.getMS()).build();
        try{
	        shell.hopper().hop(host, LITP_ADMIN);
	        response_command = shell.execute("whoami").getOutput();
	        LOGGER.info("The command whoami returns: {}", response_command );
	        state = false;
        } catch (final JSchCLIToolException | TimeoutException e) {
          LOGGER.info("Password for litp-admin needs to be changed.");
        } finally {
            shell.close();
        }

        return state;
    }

    private boolean isRootPasswordIncorrect(final Host host) {
        boolean state = true;
        LOGGER.info("Trying to access {} as root user.", hostToString(host));
        TafCliToolShell shell = TafCliTools.sshShell(HostConfigurator.getMS()).build();
        try {
            shell.hopper().hop(host, LITP_ADMIN);
            shell.writeLine("su", WaitConditions.substring("Password: "));
            shell.writeLine(NEW_PASSWORD, WaitConditions.substring("su: incorrect password"));
            LOGGER.info("Password for root needs to be changed.");
        } catch (final TimeoutException e) {
            state = false;
            LOGGER.info("The command whoami returns: {}", shell.execute("whoami").getOutput());

        } finally {
        	shell.close();
        }
        return state;
    }

    private void changeLitpAdminPassword(final Host host, final String oldPassword, final String newPassword) {
        LOGGER.info("Changing password for {}.", host.getUser());
        TafCliToolShell shell = TafCliTools.sshShell(HostConfigurator.getMS()).build();
        LOGGER.info(shell.execute("ssh-keyscan -H " + host.getIp() + " >> /root/.ssh/known_hosts").getOutput());
        shell.writeLine("ssh " + host.getUser() + "@" + host.getIp(), WaitConditions.substring(host.getUser() + "@" + host.getIp() + "'s password: "));
        shell.writeLine(oldPassword, WaitConditions.substring("(current) UNIX password: "));
        shell.writeLine(oldPassword, WaitConditions.substring("New password: "));
        shell.writeLine(newPassword, WaitConditions.substring("Retype new password: "));
        shell.writeLine(newPassword, WaitConditions.substring("passwd: all authentication tokens updated successfully."));
    	shell.close();
        LOGGER.info("The {} password has been changed", host.getUser());
    }

    private void changeRootPassword(final Host host, final String oldPassword, final String newPassword) {
        LOGGER.info("Changing password for root");
        TafCliToolShell shell = TafCliTools.sshShell(HostConfigurator.getMS()).build();
        shell.hopper().hop(host, LITP_ADMIN);
        shell.writeLine("su", WaitConditions.substring("Password: "));
        shell.writeLine(oldPassword, WaitConditions.substring("(current) UNIX password: "));
        shell.writeLine(oldPassword, WaitConditions.substring("New password: "));
        shell.writeLine(newPassword, WaitConditions.substring("Retype new password: "));
        shell.writeLine(newPassword, WaitConditions.substring("root@"));
    	shell.close();

        LOGGER.info("Root password has been changed");
    }
}
