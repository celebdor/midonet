/*
 * Copyright 2013 Midokura Europe SARL
 */

package org.midonet.midolman.state.zkManagers;

import org.midonet.midolman.state.Directory;
import org.midonet.midolman.state.StateAccessException;
import org.midonet.midolman.state.ZkManager;
import org.midonet.packets.IPv6Subnet;
import org.midonet.packets.IPv6Addr;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.ZooDefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BridgeDhcpV6ZkManager extends ZkManager {

    private static final Logger log = LoggerFactory
        .getLogger(BridgeDhcpV6ZkManager.class);

    public static class Subnet6 {
        IPv6Subnet prefix;

        /* Default constructor for deserialization. */
        public Subnet6() {
        }

        public Subnet6(IPv6Subnet prefix) {
            this.prefix = prefix;
        }

        public IPv6Subnet getPrefix() {
            return prefix;
        }

        public void setPrefix(IPv6Subnet prefix) {
            this.prefix = prefix;
        }

        @Override
        public String toString() {
            return "Subnet6{" +
                "prefix=" + prefix.toString() +
                '}';
        }
    }

    public static class Host {
        String clientId;
        IPv6Addr fixedAddress;
        String name;

        /* Default constructor for deserialization. */
        public Host() {
        }

        public Host(String clientId, IPv6Addr fixedAddress, String name) {
            this.clientId = clientId;
            this.fixedAddress = fixedAddress;
            this.name = name;
        }

        public String getClientId() {
            return clientId;
        }

        public IPv6Addr getFixedAddress() {
            return fixedAddress;
        }

        public String getName() {
            return name;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public void setFixedAddress(IPv6Addr fixedAddress) {
            this.fixedAddress = fixedAddress;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * Initializes a BridgeDhcpV6ZkManager object with a ZooKeeper client
     * and the root path of the ZooKeeper directory.
     *
     * @param zk
     *            ZooKeeper object.
     * @param basePath
     *            The root path.
     */
    public BridgeDhcpV6ZkManager(Directory zk, String basePath) {
        super(zk, basePath);
    }

    public void createSubnet6(UUID bridgeId, Subnet6 subnet)
            throws StateAccessException {
        List<Op> ops = new ArrayList<Op>();
        ops.add(Op.create(paths.getBridgeDhcpSubnet6Path(
                bridgeId, subnet.getPrefix()),
                serializer.serialize(subnet), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT));

        ops.add(Op.create(paths.getBridgeDhcpV6HostsPath(
            bridgeId, subnet.getPrefix()), null,
                          ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));
        multi(ops);
    }

    public void updateSubnet6(UUID bridgeId, Subnet6 subnet)
            throws StateAccessException {
        update(paths.getBridgeDhcpSubnet6Path(bridgeId,
                subnet.getPrefix()), serializer.serialize(subnet));
    }

    public Subnet6 getSubnet6(UUID bridgeId, IPv6Subnet prefix)
            throws StateAccessException {
        byte[] data = get(paths.getBridgeDhcpSubnet6Path(bridgeId,
                prefix), null);
        return serializer.deserialize(data, Subnet6.class);
    }

    public boolean existsSubnet6(UUID bridgeId, IPv6Subnet prefix)
            throws StateAccessException {
        return exists(paths.getBridgeDhcpSubnet6Path(bridgeId,
                prefix));
    }

    public void deleteSubnet6(UUID bridgeId, IPv6Subnet prefix)
            throws StateAccessException {
        List<Op> ops = new ArrayList<Op>();
        // Delete the hostAssignments
        List<String> hosts = listHosts(bridgeId, prefix);
        for (String clientId : hosts)
            ops.add(Op.delete(paths.getBridgeDhcpV6HostPath(
                    bridgeId, prefix, clientId), -1));
        // Delete the 'hosts' subdirectory.
        ops.add(Op.delete(
                paths.getBridgeDhcpV6HostsPath(bridgeId, prefix), -1));
        // Delete the subnet's root directory.
        ops.add(Op.delete(
                paths.getBridgeDhcpSubnet6Path(bridgeId, prefix), -1));
        multi(ops);
    }

    public List<IPv6Subnet> listSubnet6s(UUID bridgeId)
            throws StateAccessException {
        Set<String> prefixStrings = getChildren(
                paths.getBridgeDhcpV6Path(bridgeId), null);
        List<IPv6Subnet> prefixes = new ArrayList<IPv6Subnet>();
        for (String prefixStr : prefixStrings)
            prefixes.add(IPv6Subnet.fromString(prefixStr));
        return prefixes;
    }

    public List<Subnet6> getSubnet6s(UUID bridgeId)
            throws StateAccessException {
        Set<String> prefixStrings = getChildren(
                paths.getBridgeDhcpV6Path(bridgeId));
        List<Subnet6> subnets = new ArrayList<Subnet6>();
        for (String prefixStr : prefixStrings)
            subnets.add(getSubnet6(bridgeId, IPv6Subnet.fromString(prefixStr)));
        return subnets;
    }

    public void addHost(UUID bridgeId, IPv6Subnet prefix, Host host)
            throws StateAccessException {
        addPersistent(paths.getBridgeDhcpV6HostPath(
                bridgeId, prefix, host.getClientId()), serializer.serialize(host));
    }

    public void updateHost(UUID bridgeId, IPv6Subnet prefix, Host host)
            throws StateAccessException {
        update(paths.getBridgeDhcpV6HostPath(
                bridgeId, prefix, host.getClientId()), serializer.serialize(host));
    }

    public Host getHost(UUID bridgeId, IPv6Subnet prefix, String clientId)
            throws StateAccessException {
        byte[] data = get(paths.getBridgeDhcpV6HostPath(
                bridgeId, prefix, clientId), null);
        return serializer.deserialize(data, Host.class);
    }

    public void deleteHost(UUID bridgId, IPv6Subnet prefix, String clientId)
            throws StateAccessException {
        delete(paths.getBridgeDhcpV6HostPath(bridgId, prefix, clientId));
    }

    public boolean existsHost(UUID bridgeId, IPv6Subnet prefix, String clientId)
            throws StateAccessException {
        return exists(paths.getBridgeDhcpV6HostPath(
                bridgeId, prefix, clientId));
    }

    public List<String> listHosts(UUID bridgeId, IPv6Subnet prefix)
            throws StateAccessException {
        Set<String> clientIds = getChildren(
                paths.getBridgeDhcpV6HostsPath(bridgeId, prefix));
        List<String> clientIdStrings = new ArrayList<String>();
        for (String clientId : clientIds)
            clientIdStrings.add(clientId);
        return clientIdStrings;
    }

    public List<Host> getHosts(UUID bridgeId, IPv6Subnet prefix)
            throws StateAccessException {
        Set<String> clientIds = getChildren(
                paths.getBridgeDhcpV6HostsPath(bridgeId, prefix));
        List<Host> hosts = new ArrayList<Host>();
        for (String clientId : clientIds)
            hosts.add(getHost(bridgeId, prefix, clientId));
        return hosts;
    }
}