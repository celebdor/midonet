/*
* Copyright 2012 Midokura Europe SARL
*/
package org.midonet.mmdpctl.commands.results;

import org.midonet.odp.Datapath;

import java.util.Set;

public class ListDatapathsResult implements Result {
    Set<Datapath> datapaths;

    public ListDatapathsResult(Set<Datapath> datapaths) {
        this.datapaths = datapaths;
    }

    @Override
    public void printResult() {
        if (datapaths.size() > 0) {
        System.out.println("Found " + datapaths.size() + " datapaths:");
        for (Datapath datapath : datapaths) {
            System.out.println("\t"+datapath.getName());
        }
        } else {
            System.out.println("Could not find any installed datapath.");
        }
    }
}