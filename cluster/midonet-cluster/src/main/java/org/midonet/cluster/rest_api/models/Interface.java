/*
 * Copyright 2015 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.midonet.cluster.rest_api.models;

import java.net.InetAddress;
import java.util.UUID;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.google.protobuf.Message;

import org.midonet.cluster.data.ZoomClass;
import org.midonet.cluster.data.ZoomConvert;
import org.midonet.cluster.data.ZoomEnum;
import org.midonet.cluster.data.ZoomEnumValue;
import org.midonet.cluster.data.ZoomField;
import org.midonet.cluster.models.Topology;
import org.midonet.cluster.rest_api.annotation.ParentId;
import org.midonet.cluster.rest_api.annotation.Resource;
import org.midonet.cluster.rest_api.annotation.ResourceId;
import org.midonet.cluster.util.IPAddressUtil;

@XmlRootElement
@Resource(name = ResourceUris.INTERFACES, parents = { Host.class })
@ZoomClass(clazz = Topology.Host.Interface.class)
public class Interface extends UriResource {

    @ZoomEnum(clazz = Topology.Host.Interface.Type.class)
    public enum InterfaceType {
        @ZoomEnumValue(value = "PHYSICAL")
        Physical,
        @ZoomEnumValue(value = "VIRTUAL")
        Virtual,
        @ZoomEnumValue(value = "TUNNEL")
        Tunnel,
        @ZoomEnumValue(value = "UNKNOWN")
        Unknown
    }

    @ZoomEnum(clazz = Topology.Host.Interface.Endpoint.class)
    public enum Endpoint {
        @ZoomEnumValue(value = "DATAPATH_EP")
        DATAPATH,
        @ZoomEnumValue(value = "PHYSICAL_EP")
        PHYSICAL,
        @ZoomEnumValue(value = "VM_EP")
        VM,
        @ZoomEnumValue(value = "GRE_EP")
        GRE,
        @ZoomEnumValue(value = "CAPWAP_EP")
        CAPWAP,
        @ZoomEnumValue(value = "LOCALHOST_EP")
        LOCALHOST,
        @ZoomEnumValue(value = "TUNTAP_EP")
        TUNTAP,
        @ZoomEnumValue(value = "UNKNOWN_EP")
        UNKNOWN
    }

    @ZoomEnum(clazz = Topology.Host.Interface.DpPortType.class)
    public enum DpPortType {
        @ZoomEnumValue(value = "NET_DEV_DP")
        NetDev,
        @ZoomEnumValue(value = "INTERNAL_DP")
        Internal,
        @ZoomEnumValue(value = "GRE_DP")
        Gre,
        @ZoomEnumValue(value = "VXLAN_DP")
        VXLan,
        @ZoomEnumValue(value = "GRE64_DP")
        Gre64,
        @ZoomEnumValue(value = "LISP_DP")
        Lisp
    }

    @ParentId
    public UUID hostId;
    @ResourceId
    @ZoomField(name = "name")
    public String name;
    @ZoomField(name = "mac")
    public String mac;
    @ZoomField(name = "mtu")
    public int mtu;
    @XmlTransient
    @ZoomField(name = "up")
    public boolean up;
    @XmlTransient
    @ZoomField(name = "has_link")
    public boolean hasLink;
    @ZoomField(name = "type")
    public InterfaceType type;
    @ZoomField(name = "endpoint")
    public Endpoint endpoint;
    @ZoomField(name = "port_type")
    public DpPortType portType;
    @ZoomField(name = "addresses", converter = IPAddressUtil.Converter.class)
    public InetAddress[] addresses;

    public int status;

    @Override
    public void afterFromProto(Message proto) {
        super.afterFromProto(proto);
        status = (up ? 1 : 0) | (hasLink ? 2 : 0);
    }

    public void beforeToProto() {
        throw new ZoomConvert.ConvertException("Cannot create host interface");
    }

}
