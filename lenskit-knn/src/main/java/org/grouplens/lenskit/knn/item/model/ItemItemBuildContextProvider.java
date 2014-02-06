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
package org.grouplens.lenskit.knn.item.model;

import it.unimi.dsi.fastutil.longs.*;
import org.grouplens.lenskit.collections.LongKeyDomain;
import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.data.history.UserHistorySummarizer;
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Provider that sets up an {@link ItemItemBuildContext}.
 * 
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class ItemItemBuildContextProvider implements Provider<ItemItemBuildContext> {

    private static final Logger logger = LoggerFactory.getLogger(ItemItemBuildContextProvider.class);

    private final UserEventDAO userEventDAO;
    private final UserVectorNormalizer normalizer;
    private final UserHistorySummarizer userSummarizer;

    @Inject
    public ItemItemBuildContextProvider(@Transient UserEventDAO edao, 
                                        @Transient UserVectorNormalizer normalizer,
                                        @Transient UserHistorySummarizer userSummarizer) {
        userEventDAO = edao;
        this.normalizer = normalizer;
        this.userSummarizer = userSummarizer;
    }

    /**
     * Constructs and returns a new ItemItemBuildContext.
     *
     * @return a new ItemItemBuildContext.
     */
    @Override
    public ItemItemBuildContext get() {
        logger.info("constructing build context");
        logger.debug("using normalizer {}", normalizer);
        logger.debug("using summarizer {}", userSummarizer);

        logger.debug("Building item data");
        Long2ObjectMap<Long2DoubleMap> itemData = new Long2ObjectOpenHashMap<Long2DoubleMap>(1000);
        Long2ObjectMap<LongSortedSet> candidateData = new Long2ObjectOpenHashMap<LongSortedSet>(1000);
        buildItemRatings(itemData, candidateData);

        LongKeyDomain items = LongKeyDomain.fromCollection(itemData.keySet(), true);
        final int n = items.domainSize();
        assert n == itemData.size();
        // finalize the item data into vectors
        SparseVector[] itemRatings = new SparseVector[n];

        for (int i = 0; i < n; i++) {
            final long item = items.getKey(i);
            Long2DoubleMap ratings = itemData.get(item);
            SparseVector v = ImmutableSparseVector.create(ratings);
            assert v.size() == ratings.size();
            itemRatings[i] = v;
            // clear the array so GC can free
            itemData.put(item, null);
        }

        logger.debug("item data completed");
        return new ItemItemBuildContext(items, itemRatings, candidateData);
    }

    /**
     * Transpose the user matrix so we have a matrix of item ids to ratings. Accumulate user item vectors into
     * the candidate sets for each item
     *
     * @param ratings    a Long2ObjectMap<Long2DoubleMap> encoding a matrix of item ids to (userId: rating)
     *                   pairs (to be filled)
     * @param candidates a Long2ObjectMap<LongSortedSet> holding item candidate sets (to be filled)
     */
    private void buildItemRatings(Long2ObjectMap<Long2DoubleMap> ratings,
                                  Long2ObjectMap<LongSortedSet> candidates) {
        // initialize the transposed array to collect item vector data
        Cursor<UserHistory<Event>> users = userEventDAO.streamEventsByUser();
        try {
            for (UserHistory<Event> user : users) {
                long uid = user.getUserId();
                SparseVector summary = userSummarizer.summarize(user);
                MutableSparseVector normed = summary.mutableCopy();
                normalizer.normalize(uid, summary, normed);

                for (VectorEntry rating : normed.fast()) {
                    final long item = rating.getKey();
                    // get the item's rating vector
                    Long2DoubleMap ivect = ratings.get(item);
                    if (ivect == null) {
                        ivect = new Long2DoubleOpenHashMap();
                        ratings.put(item, ivect);
                    }
                    ivect.put(uid, rating.getValue());
                }

                // get the item's candidate set
                if (candidates != null) {
                    candidates.put(uid, normed.keySet());
                }
            }
        } finally {
            users.close();
        }
    }
}
