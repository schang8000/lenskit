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
package org.grouplens.lenskit.collections;

import java.util.Collection;
import java.util.Iterator;

/**
 * Collection supporting fast iteration.
 *
 * <p>A fast collection is a collection that may support <em>fast iteration</em>
 * — iteration where the same object is returned each time, having been mutated
 * to represent the next state.  It can save greatly in object allocation
 * overhead in some circumstances.  Using a fast iterator is only possible if
 * the looping code doesn't allow objects returned by the iterator to escape the
 * loop (e.g. it doesn't save them away in other objects or something), but many
 * loops only observe the object and then discard it before the next iteration.
 * Those loops benefit from fast iterators.
 *
 * @param <E> The type of value in the fast collection.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 * @compat Public
 * @deprecated Just implement {@link FastIterable}.
 */
@Deprecated
public interface FastCollection<E> extends Collection<E>, FastIterable<E> {
}
