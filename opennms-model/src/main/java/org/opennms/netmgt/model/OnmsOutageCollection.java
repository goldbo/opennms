/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2008-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.model;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * <p>OnmsOutageCollection class.</p>
 *
 * @author ranger
 * @version $Id: $
 */
@XmlRootElement(name="outages")
public class OnmsOutageCollection extends LinkedList<OnmsOutage> {

	/**
     * 
     */
    private static final long serialVersionUID = -12993787944327060L;
    private long m_totalCount;

    /**
	 * <p>Constructor for OnmsOutageCollection.</p>
	 */
	public OnmsOutageCollection() {
        super();
    }

    /**
     * <p>Constructor for OnmsOutageCollection.</p>
     *
     * @param c a {@link java.util.Collection} object.
     */
    public OnmsOutageCollection(Collection<? extends OnmsOutage> c) {
        super(c);
    }

    /**
     * <p>getNotifications</p>
     *
     * @return a {@link java.util.List} object.
     */
    @XmlElement(name="outage")
    public List<OnmsOutage> getNotifications() {
        return this;
    }

    /**
     * <p>setEvents</p>
     *
     * @param events a {@link java.util.List} object.
     */
    public void setEvents(List<OnmsOutage> events) {
        if (events == this) return;
        clear();
        addAll(events);
    }
    
    /**
     * <p>getCount</p>
     *
     * @return a {@link java.lang.Integer} object.
     */
    @XmlAttribute(name="count")
    public Integer getCount() {
    	return this.size();
    }

    /**
     * <p>getTotalCount</p>
     *
     * @return a int.
     */
    @XmlAttribute(name="totalCount")
    public long getTotalCount() {
        return m_totalCount;
    }

    /**
     * <p>setTotalCount</p>
     *
     * @param l a int.
     */
    public void setTotalCount(long l) {
        m_totalCount = l;
    }
}
