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

import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class BinaryFormatTest {
    @Test
    public void testNoFlags() {
        BinaryFormat format = BinaryFormat.create();
        assertThat(format.getFlagWord(), equalTo((short) 0));
        assertThat(format.hasTimestamps(), equalTo(false));
        assertThat(format.getRatingSize(), equalTo(24));
    }

    @Test
    public void testTimestampFlag() {
        BinaryFormat format = BinaryFormat.create(BinaryFormatFlag.TIMESTAMPS);
        assertThat(format.getFlagWord(), equalTo((short) 1));
        assertThat(format.hasTimestamps(), equalTo(true));
        assertThat(format.getRatingSize(), equalTo(32));
    }

    @Test
    public void testEqual() {
        assertThat(BinaryFormat.create(), equalTo(BinaryFormat.create()));
        assertThat(BinaryFormat.create(BinaryFormatFlag.TIMESTAMPS),
                   equalTo(BinaryFormat.create(BinaryFormatFlag.TIMESTAMPS)));
        assertThat(BinaryFormat.create(BinaryFormatFlag.TIMESTAMPS),
                   not(equalTo(BinaryFormat.create())));
    }
}
