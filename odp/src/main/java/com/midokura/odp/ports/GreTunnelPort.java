/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.odp.ports;

import javax.annotation.Nonnull;

import com.midokura.odp.Port;

/**
 * Description of a GRE tunnel datapath port.
 */
public class GreTunnelPort extends Port<GreTunnelPortOptions, GreTunnelPort> {

    public GreTunnelPort(@Nonnull String name) {
        super(name, Type.Gre);
    }

    @Override
    protected GreTunnelPort self() {
        return this;
    }

    @Override
    public GreTunnelPortOptions newOptions() {
        return new GreTunnelPortOptions();
    }

}