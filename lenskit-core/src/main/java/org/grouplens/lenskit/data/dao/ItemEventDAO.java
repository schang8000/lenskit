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
package org.grouplens.lenskit.data.dao;

import it.unimi.dsi.fastutil.longs.LongSet;
import org.grouplens.grapht.annotation.DefaultImplementation;
import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.history.ItemEventCollection;

import javax.annotation.Nullable;
import java.util.List;

/**
 * DAO to retrieve events by item.
 *
 * @since 1.3
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
@DefaultImplementation(PrefetchingItemEventDAO.class)
public interface ItemEventDAO {
    /**
     * Stream events grouped by item.
     *
     * @return A cursor iterating over the events from all items.  If an item exists but does not
     *         have any events, it may or may not be included depending on the DAO implementation.
     */
    Cursor<ItemEventCollection<Event>> streamEventsByItem();

    /**
     * Stream events grouped by item.
     *
     * @param type The type of item to look for.
     * @return A cursor iterating over the events of type {@code type} from all items.  If an item
     *         exists but does not have any events, it may or may not be included depending on the
     *         DAO implementation.
     */
    <E extends Event> Cursor<ItemEventCollection<E>> streamEventsByItem(Class<E> type);

    /**
     * Get the events for a specific item.
     * @param item The item ID.
     * @return The item's history, or {@code null} if the item is unknown.
     */
    List<Event> getEventsForItem(long item);

    /**
     * Get the events for a specific item, filtering by type.
     *
     * @param item The item ID.
     * @param type The type of events to retrieve.
     * @return The item's history, or {@code null} if the item is unknown.
     */
    @Nullable
    <E extends Event> List<E> getEventsForItem(long item, Class<E> type);

    /**
     * Get the users who have interacted with an item.
     *
     * @param item The item ID.
     * @return The set of users who have interacted with this item, or {@code null} if the item
     * is unknown.
     */
    @Nullable
    LongSet getUsersForItem(long item);
}
