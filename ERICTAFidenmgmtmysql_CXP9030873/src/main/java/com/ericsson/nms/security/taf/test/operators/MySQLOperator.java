/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.nms.security.taf.test.operators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.User;
import com.ericsson.cifwk.taf.data.exception.UserNotFoundException;
import com.ericsson.cifwk.taf.tools.cli.TafCliToolShell;
import com.ericsson.cifwk.taf.tools.cli.TafCliTools;
import com.ericsson.de.tools.cli.CliCommandResult;
import com.ericsson.de.tools.cli.WaitConditions;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.google.inject.Singleton;

@Singleton
public class MySQLOperator implements GenericOperator {

    private static final Logger logger = LoggerFactory.getLogger(MySQLOperator.class);
    private static HashMap<String, Boolean> activeDbs = new HashMap<String, Boolean>(); //cache for node's availability check
    private final ConnectionPool connectionPool = new ConnectionPool();
    private TafCliToolShell activeConnection;
    private Host hostWithActiveMysql;

    @Override
    public void checkConnectionsAndReconnectIfNecessary() {
        if (activeConnection == null) {
            return;
        }

        final Host activeConnectionHost = connectionPool.getHost(activeConnection);

        for (final Host host : connectionPool.getAllHosts()) {
            try {
            	CliCommandResult result = use(host).getShell().execute("hostname",30);
                
                final String hostname = result.getOutput().replaceAll("(\r\n|\n)", " ");
                final int exitCode = result.getExitCode();
                logger.debug("The hostname command returns: {} with the exit code: {}", hostname, exitCode);

                if (exitCode != 0) {
                    throw new IllegalStateException("Unable to execute the hostname command.");
                }
            } catch (final RuntimeException e) {
                logger.warn("Unable to execute hostname on the {}. Recovering...", hostToString(host));
                connectionPool.set(host, initializeConnection(host));
            }
        }
        activeConnection = connectionPool.get(activeConnectionHost);
    }

    @Override
    public MySQLOperator use(final Host host) {
        if (!connectionPool.isOpen(host)) {
            connectionPool.set(host, initializeConnection(host));
        }
        activeConnection = connectionPool.get(host);
        return this;
    }

    @Override
    public MySQLOperator useDb1() {
        return use(HostConfigurator.getDb1());
    }

    @Override
    public MySQLOperator useDb2() {
        return use(HostConfigurator.getDb2());
    }

    @Override
    public MySQLOperator useDbWithActiveMySQL() {
        return use(findDbWithActiveMysql());
    }

    @Override
    public TafCliToolShell getShell() throws RuntimeException {
        return activeConnection;
    }

    public static List<Host> getDbNodes() {
        final List<Host> dbNodes = new ArrayList<Host>();
        for (final Host host : HostConfigurator.getDbNodes()) {
            if (isDbNodeAccessible(host)) {
                dbNodes.add(host);
            }
        }
        return dbNodes;
    }

    @Override
    public String getHostname() {
        final Host host = connectionPool.getHost(activeConnection);
        return (host != null) ? host.getHostname() : "null";
    }

    @Override
    @Deprecated
    public String getCommand(final String command) {
        return DataHandler.getAttribute(CLI_COMMAND_PROPERTY_PREFIX + command).toString();
    }

    public static Boolean isDbNodeAccessible(final Host host) {
        if (host == null) {
            return false;
        }

        if (activeDbs.containsKey(host.getIp())) { //we don't want to check it multiple times
            return activeDbs.get(host.getIp());
        }

        logger.info("Trying to connect to the MS");
        TafCliToolShell shell = TafCliTools.sshShell(HostConfigurator.getMS()).build();

        logger.info("Checking if is possible to ping the DB node {}", hostToString(host));
        CliCommandResult result = shell.execute("ping -c 2 " + host.getIp(),30);
        final String cmdOutput = result.getOutput();

        // ping command returns: code 0 = success, 1 = no reply and 2 = other error
        final int cmdExitCode = result.getExitCode();
        final boolean availabilityStatus = cmdExitCode == 0;

        logger.debug("The command output: {}", cmdOutput);
        logger.info("The command exit code: {}", cmdExitCode);
        shell.writeLine("exit");
        shell.close();
        
        activeDbs.put(host.getIp(), availabilityStatus);
        logger.debug("The DB node {} is available: {}", hostToString(host), availabilityStatus);

        return availabilityStatus;
    }

    public static String hostToString(final Host host) {
        return "[" + host.getHostname() + ", " + host.getIp() + "]";
    }

    private TafCliToolShell initializeConnection(final Host host) {
        final int sleepTime = 5000;
        try {
            return connectWithDb(host);
        } catch (final Exception exception) {
            logger.warn("Unable to initialize connection to the {}. Waiting {} ms before the second attempt.", hostToString(host), sleepTime);
            logger.debug("The root exception: ", exception);
        }
        try {
            Thread.sleep(sleepTime);
        } catch (final InterruptedException e) {
            logger.warn("Other thread has interrupted the current thread.", e);
        }
        return connectWithDb(host);
    }

    private TafCliToolShell connectWithDb(final Host host) throws RuntimeException {
        User root = USER_ROOT;
        try {
            root = host.getUser(USER_ROOT.getUsername());
        } catch (final Exception e) {
            if (e instanceof UserNotFoundException) {
                logger.warn("User {} does not exists in DMT", USER_ROOT.getUsername());
            } else {
                logger.error("Unexpected exception", e);
                e.printStackTrace();
            }
        }
        TafCliToolShell shell = TafCliTools.sshShell(HostConfigurator.getMS()).build();
        shell.hopper().hop(host, host.getUser("litp-admin"));
//        shell.writeLine("su - " + root.getUsername(), WaitConditions.substring("Password: "));
//        shell.writeLine(PASSWORD);
        shell.switchUser("root", "12shroot");
        return shell;
    }

    private Host findDbWithActiveMysql() throws IllegalStateException {
        if (hostWithActiveMysql != null) {
            return hostWithActiveMysql;
        }

        final String mysqlClusterStatus = "hagrp -state | grep db_cluster_mysql | grep `hostname` | awk '{print $4}'";

        for (final Host host : getDbNodes()) {
            try {
            	CliCommandResult result = use(host).getShell().execute(mysqlClusterStatus,30);
                final String output = result.getOutput();

                if (output.contains("ONLINE")) {
                    hostWithActiveMysql = host;
                    return hostWithActiveMysql;
                }
            } catch (final Exception e) {
                logger.info("The DB node {} is unavailable.", host);
            }
        }
        throw new IllegalStateException("No active MySQL instances.");
    }
}
