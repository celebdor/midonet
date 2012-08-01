/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.sdn.dp.ports;

import javax.annotation.Nonnull;

import com.midokura.sdn.dp.Port;

/**
 * Description to an internal datapath port.
 */
public class InternalPort extends Port<InternalPortOptions, InternalPort> {

    public InternalPort(@Nonnull String name) {
        super(name, Type.Internal);
    }

    @Override
    public InternalPortOptions newOptions() {
        return new InternalPortOptions();
    }

    @Override
    protected InternalPort self() {
        return this;
    }

}
