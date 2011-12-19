/*
 * @(#)DaoFactory        1.6 11/11/15
 *
 * Copyright 2011 Midokura KK
 */
package com.midokura.midolman.mgmt.data;

import com.midokura.midolman.mgmt.data.dao.AdRouteDao;
import com.midokura.midolman.mgmt.data.dao.AdminDao;
import com.midokura.midolman.mgmt.data.dao.BgpDao;
import com.midokura.midolman.mgmt.data.dao.BridgeDao;
import com.midokura.midolman.mgmt.data.dao.ChainDao;
import com.midokura.midolman.mgmt.data.dao.PortDao;
import com.midokura.midolman.mgmt.data.dao.RouteDao;
import com.midokura.midolman.mgmt.data.dao.RouterDao;
import com.midokura.midolman.mgmt.data.dao.RuleDao;
import com.midokura.midolman.mgmt.data.dao.TenantDao;
import com.midokura.midolman.mgmt.data.dao.VifDao;
import com.midokura.midolman.mgmt.data.dao.VpnDao;
import com.midokura.midolman.state.StateAccessException;

/**
 * ZooKeeper DAO factory interface.
 *
 * @version 1.6 15 Nov 2011
 * @author Ryu Ishimoto
 */
public interface DaoFactory {

    /**
     * Get Admin DAO
     *
     * @return AdminDao object
     * @throws StateAccessException
     *             Data access error.
     */
    AdminDao getAdminDao() throws StateAccessException;

    /**
     * Get ad route DAO
     *
     * @return AdRouteDao object
     * @throws StateAccessException
     *             Data access error.
     */
    AdRouteDao getAdRouteDao() throws StateAccessException;

    /**
     * Get BGP DAO
     *
     * @return BgpDao object
     * @throws StateAccessException
     *             Data access error.
     */
    BgpDao getBgpDao() throws StateAccessException;

    /**
     * Get bridge DAO
     *
     * @return BridgeDao object
     * @throws StateAccessException
     *             Data access error.
     */
    BridgeDao getBridgeDao() throws StateAccessException;

    /**
     * Get chain DAO
     *
     * @return ChainDao object
     * @throws StateAccessException
     *             Data access error.
     */
    ChainDao getChainDao() throws StateAccessException;

    /**
     * Get port DAO
     *
     * @return PortDao object
     * @throws StateAccessException
     *             Data access error.
     */
    PortDao getPortDao() throws StateAccessException;

    /**
     * Get route DAO
     *
     * @return RouteDao object
     * @throws StateAccessException
     *             Data access error.
     */
    RouteDao getRouteDao() throws StateAccessException;

    /**
     * Get router DAO
     *
     * @return RouterDao object
     * @throws StateAccessException
     *             Data access error.
     */
    RouterDao getRouterDao() throws StateAccessException;

    /**
     * Get rule DAO
     *
     * @return RuleDao object
     * @throws StateAccessException
     *             Data access error.
     */
    RuleDao getRuleDao() throws StateAccessException;

    /**
     * Get tenant DAO
     *
     * @return TenantDao object
     * @throws StateAccessException
     *             Data access error.
     */
    TenantDao getTenantDao() throws StateAccessException;

    /**
     * Get VIF DAO
     *
     * @return VifDao object
     * @throws StateAccessException
     *             Data access error.
     */
    VifDao getVifDao() throws StateAccessException;

    /**
     * Get VPN DAO
     *
     * @return VpnDao object
     * @throws StateAccessException
     *             Data access error.
     */
    VpnDao getVpnDao() throws StateAccessException;

}
