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

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.grouplens.grapht.annotation.DefaultProvider;
import org.grouplens.lenskit.collections.LongKeyDomain;
import org.grouplens.lenskit.core.Shareable;
import org.grouplens.lenskit.scored.ScoredId;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Item-item similarity model using an in-memory similarity matrix.
 *
 * <p/>
 * These similarities are post-normalization, so code using them
 * should use the same normalizations used by the builder to make use of the
 * similarity scores.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 * @since 0.10
 */
@DefaultProvider(ItemItemModelBuilder.class)
@Shareable
public class SimilarityMatrixModel implements Serializable, ItemItemModel {
    private static final long serialVersionUID = 3L;

    private final LongKeyDomain itemDomain;
    private final List<List<ScoredId>> neighborhoods;

    /**
     * Construct a new item-item model.
     *
     * @param items The item domain.
     * @param nbrs  The item neighborhoods.
     */
    public SimilarityMatrixModel(LongKeyDomain items, List<List<ScoredId>> nbrs) {
        itemDomain = items.clone();
        neighborhoods = ImmutableList.copyOf(nbrs);
    }

    @Override
    public LongSortedSet getItemUniverse() {
        return itemDomain.activeSetView();
    }

    @Override
    @Nonnull
    public List<ScoredId> getNeighbors(long item) {
        int idx = itemDomain.getIndex(item);
        if (idx < 0) {
            return Collections.emptyList();
        } else {
            return neighborhoods.get(idx);
        }
    }
}
