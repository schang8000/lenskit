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
package org.grouplens.lenskit.knn.user;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.longs.*;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.event.Ratings;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.knn.NeighborhoodSize;
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer;
import org.grouplens.lenskit.transform.threshold.Threshold;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Neighborhood finder that does a fresh search over the data source ever time.
 *
 * <p>This rating vector has support for caching user rating vectors, where it
 * avoids rebuilding user rating vectors for users with no changed user. When
 * caching is enabled, it assumes that the underlying data is timestamped and
 * that the timestamps are well-behaved: if a rating has been added after the
 * currently cached rating vector was computed, then its timestamp is greater
 * than any timestamp seen while computing the cached vector.
 *
 * <p>Currently, this cache is never cleared. This should probably be changed
 * sometime.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
@ThreadSafe
public class SimpleNeighborhoodFinder implements NeighborhoodFinder, Serializable {
    private static final long serialVersionUID = 2L;
    private static final Logger logger = LoggerFactory.getLogger(SimpleNeighborhoodFinder.class);

    private final UserEventDAO userDAO;
    private final ItemEventDAO itemDAO;
    private final int neighborhoodSize;
    private final UserSimilarity similarity;
    private final UserVectorNormalizer normalizer;
    private final Threshold userThreshold;

    /**
     * Construct a new user neighborhood finder.
     *
     * @param udao  The user-event DAO.
     * @param idao  The item-event DAO.
     * @param nnbrs The number of neighbors to consider for each item.
     * @param sim   The similarity function to use.
     */
    @Inject
    public SimpleNeighborhoodFinder(UserEventDAO udao, ItemEventDAO idao,
                                    @NeighborhoodSize int nnbrs,
                                    UserSimilarity sim,
                                    UserVectorNormalizer norm,
                                    @UserSimilarityThreshold Threshold thresh) {
        userDAO = udao;
        itemDAO = idao;
        neighborhoodSize = nnbrs;
        similarity = sim;
        normalizer = norm;
        userThreshold = thresh;
        Preconditions.checkArgument(sim.isSparse(), "user similarity function is not sparse");
    }

    /**
     * Find the neighbors for a user with respect to a collection of items.
     * For each item, the {@var neighborhoodSize} users closest to the
     * provided user are returned.
     *
     * @param user  The user's rating vector.
     * @param items The items for which neighborhoods are requested.
     * @return A mapping of item IDs to neighborhoods.
     */
    @Override
    public Long2ObjectMap<? extends Collection<Neighbor>>
    findNeighbors(@Nonnull UserHistory<? extends Event> user, @Nonnull LongSet items) {
        Preconditions.checkNotNull(user, "user profile");
        Preconditions.checkNotNull(user, "item set");

        Long2ObjectMap<PriorityQueue<Neighbor>> heaps = new Long2ObjectOpenHashMap<PriorityQueue<Neighbor>>(items.size());
        for (LongIterator iter = items.iterator(); iter.hasNext();) {
            long item = iter.nextLong();
            heaps.put(item, new PriorityQueue<Neighbor>(neighborhoodSize + 1,
                                                        Neighbor.SIMILARITY_COMPARATOR));
        }

        SparseVector urs = RatingVectorUserHistorySummarizer.makeRatingVector(user);
        final long uid1 = user.getUserId();
        // freeze vector to make neighbor-finding a bit faster
        ImmutableSparseVector nratings = normalizer.normalize(user.getUserId(), urs, null)
                                                   .freeze();

        // Find candidate neighbors
        LongSet users = findCandidateNeighbors(user.getUserId(), nratings.keySet(), items);

        logger.debug("Found {} candidate neighbors", users.size());

        LongIterator uiter = users.iterator();
        while (uiter.hasNext()) {
            final long uid2 = uiter.nextLong();
            SparseVector urv = getUserRatingVector(uid2);
            if (urv == null)
                continue;

            MutableSparseVector nurv = normalizer.normalize(uid2, urv, null);

            final double sim = similarity.similarity(uid1, nratings, uid2, nurv);
            if (Double.isNaN(sim) || Double.isInfinite(sim) || !userThreshold.retain(sim)) {
                continue;
            }
            final Neighbor n = new Neighbor(uid2, urv, sim);

            for (VectorEntry e: urv.fast()) {
                final long item = e.getKey();
                PriorityQueue<Neighbor> heap = heaps.get(item);
                if (heap != null) {
                    heap.add(n);
                    if (heap.size() > neighborhoodSize) {
                        assert heap.size() == neighborhoodSize + 1;
                        heap.remove();
                    }
                }
            }
        }
        return heaps;
    }

    /**
     * Find users who may have rated items both in the user's rated item set and a target item
     * set.  It will only query based on one set, so the users may or may not have rated both a
     * target item and a source item.  This method tries to be efficient, so it uses the shorter
     * list of items to find users.
     *
     * @param user    The current user's ID (excluded from the returned set).
     * @param userItems The items the user has rated.
     * @param itemSet The set of items to look for.
     * @return The set of candidate neighbors.
     */
    protected LongSet findCandidateNeighbors(long user, LongSet userItems, LongCollection itemSet) {
        LongSet users = new LongOpenHashSet(100);

        LongIterator items;
        if (userItems.size() < itemSet.size()) {
            items = userItems.iterator();
        } else {
            items = itemSet.iterator();
        }
        while (items.hasNext()) {
            LongSet iusers = itemDAO.getUsersForItem(items.nextLong());
            if (iusers != null) {
                users.addAll(iusers);
            }
        }
        users.remove(user);

        return users;
    }

    /**
     * @param user The user ID.
     * @return The user's rating vector.
     */
    private synchronized SparseVector getUserRatingVector(long user) {
        List<Rating> ratings = userDAO.getEventsForUser(user, Rating.class);
        if (ratings == null){
             return null;
        }
        return Ratings.userRatingVector(ratings);
    }
}
