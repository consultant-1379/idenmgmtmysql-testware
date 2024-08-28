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

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.User;
import com.ericsson.cifwk.taf.data.UserType;
import com.ericsson.cifwk.taf.tools.cli.TafCliToolShell;

public interface GenericOperator {
    final String CLI_COMMAND_PROPERTY_PREFIX = "clicommand.";
    final String PASSWORD = "12shroot";
    final User USER_LITP_ADMIN = new User("litp-admin", PASSWORD, UserType.ADMIN);
    final User USER_ROOT = new User("root", PASSWORD, UserType.ADMIN);

    void checkConnectionsAndReconnectIfNecessary();

    GenericOperator use(Host host);

    GenericOperator useDb1();

    GenericOperator useDb2();

    GenericOperator useDbWithActiveMySQL();

    TafCliToolShell getShell();

    String getHostname();

    String getCommand(String command);
}
