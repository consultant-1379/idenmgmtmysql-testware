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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.tools.cli.TafCliToolShell;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * Used for utilizing the available and open ssh connections with the DB nodes
 */
final class ConnectionPool {
    private final BiMap<String, TafCliToolShell> available = HashBiMap.create();
    private final Map<String, Host> hosts = new HashMap<String, Host>();

    public boolean isOpen(final Host host) {
        return available.containsKey(host.getHostname()) && available.get(host.getHostname()) == null;
    }

    public void set(final Host host, final TafCliToolShell shell) {
        available.put(host.getHostname(), shell);
        hosts.put(host.getHostname(), host);
    }

    public TafCliToolShell get(final Host host) {
        return get(host.getHostname());
    }

    public TafCliToolShell get(final String hostname) {
        return available.get(hostname);
    }

    public Collection<Host> getAllHosts() {
        return hosts.values();
    }

    public Host getHost(final TafCliToolShell shell) {
        return hosts.get(available.inverse().get(shell));
    }
}