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
package org.grouplens.lenskit.data.dao.packed;

import org.apache.commons.lang3.SerializationUtils;
import org.grouplens.lenskit.cursors.Cursors;
import org.grouplens.lenskit.data.dao.SortOrder;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.event.Ratings;
import org.grouplens.lenskit.data.history.UserHistory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class BinaryRatingDAOTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testEmptyDAO() throws IOException {
        File file = folder.newFile("ratings.bin");
        BinaryRatingPacker packer = BinaryRatingPacker.open(file);
        packer.close();

        BinaryRatingDAO dao = BinaryRatingDAO.open(file);
        assertThat(Cursors.makeList(dao.streamEvents()),
                   hasSize(0));
        assertThat(dao.getUserIds(), hasSize(0));
        assertThat(dao.getItemIds(), hasSize(0));
    }

    @Test
    public void testSimpleDAO() throws IOException {
        File file = folder.newFile("ratings.bin");
        BinaryRatingPacker packer = BinaryRatingPacker.open(file);
        try {
            populateSimpleDAO(packer);
        } finally {
            packer.close();
        }

        BinaryRatingDAO dao = BinaryRatingDAO.open(file);
        verifySimpleDAO(dao);
    }

    @Test
    public void testSerializedDAO() throws IOException {
        File file = folder.newFile("ratings.bin");
        BinaryRatingPacker packer = BinaryRatingPacker.open(file);
        try {
            populateSimpleDAO(packer);
        } finally {
            packer.close();
        }

        BinaryRatingDAO dao = BinaryRatingDAO.open(file);
        BinaryRatingDAO clone = SerializationUtils.clone(dao);
        verifySimpleDAO(clone);
    }

    private void populateSimpleDAO(BinaryRatingPacker packer) throws IOException {
        packer.writeRating(Ratings.make(42, 105, 3.5));
        packer.writeRating(Ratings.make(42, 120, 2.5));
        packer.writeRating(Ratings.make(39, 120, 4.5));
    }

    private void verifySimpleDAO(BinaryRatingDAO dao) {
        assertThat(Cursors.makeList(dao.streamEvents()),
                   hasSize(3));
        assertThat(dao.getUserIds(), containsInAnyOrder(42L, 39L));
        assertThat(dao.getItemIds(), containsInAnyOrder(105L, 120L));
        assertThat(dao.getUsersForItem(105), containsInAnyOrder(42L));
        assertThat(dao.getUsersForItem(120), containsInAnyOrder(42L, 39L));
        assertThat(dao.getEventsForUser(39, Rating.class),
                   contains(Ratings.make(39, 120, 4.5)));
        assertThat(dao.getEventsForUser(42, Rating.class),
                   containsInAnyOrder(Ratings.make(42, 120, 2.5),
                                      Ratings.make(42, 105, 3.5)));
        assertThat(dao.getEventsForItem(105, Rating.class),
                   contains(Ratings.make(42, 105, 3.5)));
        assertThat(dao.getEventsForItem(120, Rating.class),
                   containsInAnyOrder(Ratings.make(39, 120, 4.5),
                                      Ratings.make(42, 120, 2.5)));
        assertThat(dao.getEventsForItem(42), nullValue());
        assertThat(dao.getEventsForUser(105), nullValue());

        List<UserHistory<Event>> histories = Cursors.makeList(dao.streamEventsByUser());
        assertThat(histories, hasSize(2));
        assertThat(histories.get(0).getUserId(), equalTo(39L));
        assertThat(histories.get(0),
                   equalTo(dao.getEventsForUser(39)));
        assertThat(histories.get(1).getUserId(), equalTo(42L));
        assertThat(histories.get(1),
                   equalTo(dao.getEventsForUser(42)));

        List<Rating> events = Cursors.makeList(dao.streamEvents(Rating.class, SortOrder.USER));
        assertThat(events, hasSize(3));
        assertThat(events.get(0).getUserId(), equalTo(39L));

        events = Cursors.makeList(dao.streamEvents(Rating.class, SortOrder.ITEM));
        assertThat(events, hasSize(3));
        assertThat(events.get(0).getUserId(), equalTo(42L));
        assertThat(events.get(0).getItemId(), equalTo(105L));
    }
}
