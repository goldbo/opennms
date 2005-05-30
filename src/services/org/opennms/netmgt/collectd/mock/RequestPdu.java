//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2005 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
// OpenNMS Licensing       <license@opennms.org>
//     http://www.opennms.org/
//     http://www.opennms.com/
//
package org.opennms.netmgt.collectd.mock;

import java.util.ArrayList;
import java.util.List;

import org.opennms.netmgt.collectd.SnmpObjId;

abstract public class RequestPdu extends TestPdu {

    protected SnmpObjId getRespObjIdFromReqObjId(TestAgent agent, SnmpObjId reqObjId) {
        return agent.getFollowingObjId(reqObjId);
    }

    protected int getNonRepeaters() {
        return size();
    }

    protected int getMaxRepititions() {
        return 0;
    }

    /*
     * This simulates send a packet and waiting for a response. 
     * 
     * This is a template method based on te getBulk algorithm. We use the getBulk
     * algorithm for get and nexts as well.  nonRepeaters for gets and nexts is always
     * equals to pdu size so there are no repeaters. maxRepitions is also always zero
     * for gets and nexts.
     * 
     * The method getRespObjIdFromReqObjId which by default goes 'next' is overridden
     * and does 'get' in the GetPdu.
     */
    public ResponsePdu send(TestAgent agent) {
        ResponsePdu resp = TestPdu.getResponse();
        
        // first do non repeaters
        int nonRepeaters = Math.min(size(), getNonRepeaters());
        for(int i = 0; i < nonRepeaters; i++) {
            TestVarBind varBind = (TestVarBind) getVarBindAt(i);
            SnmpObjId lastOid = varBind.getObjId();
            SnmpObjId objId = getRespObjIdFromReqObjId(agent, lastOid);
            resp.addVarBind(objId, agent.getValueFor(objId));
        }
        
        // make a list to track the repititions
        int repeaters = size() - nonRepeaters;
        List repeaterList = new ArrayList(repeaters);
        for(int i = nonRepeaters; i < size(); i++) {
            repeaterList.add(getVarBindAt(i).getObjId());
        }
        
        // now generate varbinds for the repeaters
        for(int count = 0; count < getMaxRepititions(); count++) {
            for(int i = 0; i < repeaterList.size(); i++) {
                SnmpObjId lastOid = (SnmpObjId)repeaterList.get(i);
                SnmpObjId objId = getRespObjIdFromReqObjId(agent, lastOid);
                resp.addVarBind(objId, agent.getValueFor(objId));
                repeaterList.set(i, objId);
            }
        }
        return resp;
    }
    
}