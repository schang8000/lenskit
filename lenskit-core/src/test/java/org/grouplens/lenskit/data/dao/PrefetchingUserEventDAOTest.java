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

import com.google.common.collect.Lists;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.event.Ratings;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class PrefetchingUserEventDAOTest {
    @Test
    public void testGetEvents() {
        List<Rating> ratings = Lists.newArrayList(
                Ratings.make(1, 2, 3.5),
                Ratings.make(1, 3, 4),
                Ratings.make(2, 2, 3)
        );
        EventDAO dao = EventCollectionDAO.create(ratings);
        PrefetchingUserEventDAO iedao = new PrefetchingUserEventDAO(dao);
        assertThat(iedao.getEventsForUser(2), hasSize(1));
        assertThat(iedao.getEventsForUser(1), hasSize(2));
        assertThat(iedao.getEventsForUser(4), nullValue());
    }
}
