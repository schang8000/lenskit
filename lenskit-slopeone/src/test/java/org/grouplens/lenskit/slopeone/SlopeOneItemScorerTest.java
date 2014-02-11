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
package org.grouplens.lenskit.slopeone;

import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.RecommenderBuildException;
import org.grouplens.lenskit.core.LenskitConfiguration;
import org.grouplens.lenskit.core.LenskitRecommenderEngine;
import org.grouplens.lenskit.data.dao.EventCollectionDAO;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.event.Ratings;
import org.grouplens.lenskit.data.pref.PreferenceDomain;
import org.grouplens.lenskit.data.pref.PreferenceDomainBuilder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SlopeOneItemScorerTest {

    private static final double EPSILON = 1.0e-6;

    @Test
    public void testPredict1() throws RecommenderBuildException {

        List<Rating> rs = new ArrayList<Rating>();
        rs.add(Ratings.make(1, 6, 4));
        rs.add(Ratings.make(2, 6, 2));
        rs.add(Ratings.make(1, 7, 3));
        rs.add(Ratings.make(2, 7, 2));
        rs.add(Ratings.make(3, 7, 5));
        rs.add(Ratings.make(4, 7, 2));
        rs.add(Ratings.make(1, 8, 3));
        rs.add(Ratings.make(2, 8, 4));
        rs.add(Ratings.make(3, 8, 3));
        rs.add(Ratings.make(4, 8, 2));
        rs.add(Ratings.make(5, 8, 3));
        rs.add(Ratings.make(6, 8, 2));
        rs.add(Ratings.make(1, 9, 3));
        rs.add(Ratings.make(3, 9, 4));

        LenskitConfiguration config = new LenskitConfiguration();
        config.bind(EventDAO.class).to(EventCollectionDAO.create(rs));
        config.bind(ItemScorer.class).to(SlopeOneItemScorer.class);
        config.bind(PreferenceDomain.class).to(new PreferenceDomainBuilder(1, 5)
                                                       .setPrecision(1)
                                                       .build());
        ItemScorer predictor = LenskitRecommenderEngine.build(config)
                                                       .createRecommender()
                                                       .getItemScorer();

        assertEquals(7 / 3.0, predictor.score(2, 9), EPSILON);
        assertEquals(13 / 3.0, predictor.score(3, 6), EPSILON);
        assertEquals(2, predictor.score(4, 6), EPSILON);
        assertEquals(2, predictor.score(4, 9), EPSILON);
        assertEquals(2.5, predictor.score(5, 6), EPSILON);
        assertEquals(3, predictor.score(5, 7), EPSILON);
        assertEquals(3.5, predictor.score(5, 9), EPSILON);
        assertEquals(1.5, predictor.score(6, 6), EPSILON);
        assertEquals(2, predictor.score(6, 7), EPSILON);
        assertEquals(2.5, predictor.score(6, 9), EPSILON);
    }

    @Test
    public void testPredict2() throws RecommenderBuildException {
        List<Rating> rs = new ArrayList<Rating>();
        rs.add(Ratings.make(1, 4, 3.5));
        rs.add(Ratings.make(2, 4, 5));
        rs.add(Ratings.make(3, 5, 4.25));
        rs.add(Ratings.make(2, 6, 3));
        rs.add(Ratings.make(1, 7, 4));
        rs.add(Ratings.make(2, 7, 4));
        rs.add(Ratings.make(3, 7, 1.5));

        LenskitConfiguration config = new LenskitConfiguration();
        config.bind(EventDAO.class).to(EventCollectionDAO.create(rs));
        config.bind(ItemScorer.class).to(SlopeOneItemScorer.class);
        config.bind(PreferenceDomain.class).to(new PreferenceDomainBuilder(1, 5)
                                                       .setPrecision(1)
                                                       .build());
        ItemScorer predictor = LenskitRecommenderEngine.build(config)
                                                       .createRecommender()
                                                       .getItemScorer();

        assertEquals(5, predictor.score(1, 5), EPSILON);
        assertEquals(2.25, predictor.score(1, 6), EPSILON);
        assertEquals(5, predictor.score(2, 5), EPSILON);
        assertEquals(1.75, predictor.score(3, 4), EPSILON);
        assertEquals(1, predictor.score(3, 6), EPSILON);
    }
}
