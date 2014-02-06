/*
 * LensKit, an open source recommender systems toolkit.
 * Copyright 2010-2013 Regents of the University of Minnesota and contributors
 * Work on LensKit has been funded by the National Science Foundation under
 * grants IIS 05-34939, 08-08692, 08-12148, and 10-17697.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.grouplens.lenskit.data.event;

import org.grouplens.lenskit.data.pref.Preference;

import javax.annotation.concurrent.Immutable;

/**
 * Simple implementation of a null rating (unrate event).
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 * @see SimpleRating
 * @compat Public
 */
@Immutable
final class SimpleNullRating implements Rating {
    private final long userId;
    private final long itemId;
    private final long timestamp;

    /**
     * Construct a new null rating.
     *
     * @param uid The user ID.
     * @param iid The item ID.
     * @param ts  The event timestamp.
     */
    SimpleNullRating(long uid, long iid, long ts) {
        userId = uid;
        itemId = iid;
        timestamp = ts;
    }
    
    @Override 
    public final boolean hasValue() {
        return false;
    }
    
    @Override
    public long getUserId() {
        return userId;
    }

    @Override
    public long getItemId() {
        return itemId;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public Preference getPreference() {
        return null;
    }

    @Override
    public final double getValue() throws IllegalStateException {
        String msg = "There is no rating";
        throw new IllegalStateException(msg);
    }
    
    @Override
    public boolean equals(Object o) {
        return o instanceof Rating && Ratings.equals(this, (Rating) o);
    }

    @Override
    public int hashCode() {
        return Ratings.hashRating(this);
    }

    @Override
    public String toString() {
        return String.format("Rating(u=%d, i=%d, v=null, ts=%d", userId, itemId, timestamp);
    }
}
