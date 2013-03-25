/* Copyright 2011 Midokura Inc. */

package org.midonet.midolman.state;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import org.midonet.packets.MAC;

public class MacPortMap extends ReplicatedMap<MAC, UUID> {

    public MacPortMap(Directory dir) {
        super(dir);
    }

    @Override
    protected String encodeKey(MAC key) {
        return key.toString();
    }

    @Override
    protected MAC decodeKey(String str) {
        return MAC.fromString(str);
        // TODO: Test this.
    }

    @Override
    protected String encodeValue(UUID value) {
        return value.toString();
    }

    @Override
    protected UUID decodeValue(String str) {
        return UUID.fromString(str);
    }

    public static Map<MAC, UUID> getAsMap(Directory dir)
        throws StateAccessException {
        try {
            Iterable<String> paths = dir.getChildren("/", null);
            Map<MAC, UUID> m = new HashMap<MAC, UUID>();
            for (String path : paths) {
                String[] parts = ReplicatedMap.getKeyValueVersion(path);
                // TODO(pino): consider the version too.
                m.put(MAC.fromString(parts[0]), UUID.fromString(parts[1]));
            }
            return m;
        } catch (KeeperException e) {
            throw new StateAccessException(e);
        } catch (InterruptedException e) {
            throw new StateAccessException(e);
        }
    }

    public static boolean hasPersistentEntry(Directory dir, MAC key,
                                             UUID value)
        throws StateAccessException {
        // Version 1 is used for all persistent entries added to the map.
        // This avoids having to enumerate the map entries in order to delete.
        String path = ReplicatedMap.encodeFullPath(
            key.toString(), value.toString(), 1);
        try {
            return dir.has(path);
        } catch (KeeperException e) {
            throw new StateAccessException(e);
        } catch (InterruptedException e) {
            throw new StateAccessException(e);
        }
    }

    public static void addPersistentEntry(Directory dir, MAC key, UUID value)
        throws StateAccessException {
        // Use version 1 for all persistent entries added to the map.
        // This avoids having to enumerate the map entries in order to delete.
        String path = ReplicatedMap.encodeFullPath(
            key.toString(), value.toString(), 1);
        try {
            dir.add(path, null, CreateMode.PERSISTENT);
        } catch (KeeperException e) {
            throw new StateAccessException(e);
        } catch (InterruptedException e) {
            throw new StateAccessException(e);
        }
    }

    public static void deletePersistentEntry(Directory dir, MAC key,
                                             UUID value)
        throws StateAccessException {
        // Version 1 is used for all persistent entries added to the map.
        // This avoids having to enumerate the map entries in order to delete.
        String path = ReplicatedMap.encodeFullPath(
            key.toString(), value.toString(), 1);
        try {
            dir.delete(path);
        } catch (KeeperException e) {
            throw new StateAccessException(e);
        } catch (InterruptedException e) {
            throw new StateAccessException(e);
        }
    }
}
