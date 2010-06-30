/*
 * This file is part of the OpenNMS(R) Application.
 *
 * OpenNMS(R) is Copyright (C) 2009 The OpenNMS Group, Inc.  All rights reserved.
 * OpenNMS(R) is a derivative work, containing both original code, included code and modified
 * code that was published under the GNU General Public License. Copyrights for modified
 * and included code are below.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * For more information contact:
 * OpenNMS Licensing       <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 */
package org.opennms.web.filter;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

/**
 * <p>Abstract InFilter class.</p>
 *
 * @author ranger
 * @version $Id: $
 * @since 1.8.1
 */
public abstract class InFilter<T> extends MultiArgFilter<T> {
    
    /**
     * <p>Constructor for InFilter.</p>
     *
     * @param filterType a {@link java.lang.String} object.
     * @param type a {@link org.opennms.web.filter.SQLType} object.
     * @param fieldName a {@link java.lang.String} object.
     * @param propertyName a {@link java.lang.String} object.
     * @param values an array of T objects.
     * @param <T> a T object.
     */
    public InFilter(String filterType, SQLType<T> type, String fieldName, String propertyName, T[] values){
        super(filterType, type, fieldName, propertyName, values);
    }
    
    /** {@inheritDoc} */
    @Override
    public Criterion getCriterion() {
        return Restrictions.in(getPropertyName(), getValuesAsList());
    }
    
    /** {@inheritDoc} */
    @Override
    public String getSQLTemplate() {
        StringBuilder buf = new StringBuilder(" ");
        buf.append(getSQLFieldName());
        buf.append(" IN (");
        T[] values = getValues();
        
        for(int i = 0; i < values.length; i++) {
            if (i != 0) {
                buf.append(", ");
            }
            buf.append("%s");
        }
        buf.append(") ");
        return buf.toString();
    }

}
