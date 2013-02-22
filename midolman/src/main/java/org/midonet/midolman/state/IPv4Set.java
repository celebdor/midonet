// Copyright 2012 Midokura Inc.

package org.midonet.midolman.state;

import org.apache.zookeeper.CreateMode;

import org.midonet.packets.IntIPv4;

public class IPv4Set extends ReplicatedSet<IntIPv4> {
    public IPv4Set(Directory d, CreateMode createMode) {
        super(d, createMode);
    }

    protected String encode(IntIPv4 item) { return item.toString(); }
    protected IntIPv4 decode(String str) { return IntIPv4.fromString(str); }
}