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
package org.grouplens.lenskit.data.pref;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.grouplens.lenskit.collections.CollectionUtils;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.vectors.MutableSparseVector;

import java.util.Collection;

/**
 * Utility class for working with preferences.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public final class Preferences {
    private Preferences() {}

    /**
     * Construct a new preference builder.
     * @return A new preference builder.
     */
    public static PreferenceBuilder newBuilder() {
        return new PreferenceBuilder();
    }

    /**
     * Create a new preference.
     * @param uid The user ID.
     * @param iid The item ID.
     * @param val The value.
     * @return A preference with the specified properties.
     */
    public static Preference make(long uid, long iid, double val) {
        return new SimplePreference(uid, iid, val);
    }

    /**
     * Compute a user preference vector.
     *
     * @param prefs The user's preferences.
     * @return A vector of the preferences.
     * @throws IllegalArgumentException if the same item appears multiple times, or there are
     *                                  preferences from multiple users.
     */
    public static MutableSparseVector userPreferenceVector(Collection<? extends Preference> prefs) {
        // find keys and pre-validate data
        Long2DoubleOpenHashMap prefMap = new Long2DoubleOpenHashMap(prefs.size());
        long user = 0;
        for (Preference p : CollectionUtils.fast(prefs)) {
            final long iid = p.getItemId();
            if (prefMap.isEmpty()) {
                user = p.getUserId();
            } else if (user != p.getUserId()) {
                throw new IllegalArgumentException("multiple user IDs in pref array");
            }
            if (prefMap.containsKey(iid)) {
                throw new IllegalArgumentException("item " + iid + " occurs multiple times");
            } else {
                prefMap.put(iid, p.getValue());
            }
        }

        return MutableSparseVector.create(prefMap);
    }

    /**
     * Compute the hash code of a preference.  Used to implement {@link #hashCode()} in preference
     * implementations.
     * @param preference The preference.
     * @return The preference's hash code.
     */
    public static int hashPreference(Preference preference) {
        HashCodeBuilder hcb = new HashCodeBuilder();
        return hcb.append(preference.getUserId())
                  .append(preference.getItemId())
                  .append(preference.getValue())
                  .toHashCode();
    }

    /**
     * Compare two preferences for equality.  Used to implement {@link #equals(Object)} in preference
     * implementations.
     * @param p1 The first preference.
     * @param p2 The second preference.
     * @return Whether the two preferences are equal.
     */
    public static boolean equals(Preference p1, Preference p2) {
        if (p1 == p2) {
            return true;
        } else if (p1 == null || p2 == null) {
            return false;
        }

        EqualsBuilder eqb = new EqualsBuilder();
        return eqb.append(p1.getUserId(), p2.getUserId())
                  .append(p1.getItemId(), p2.getItemId())
                  .append(p1.getValue(), p2.getValue())
                  .isEquals();
    }
}
