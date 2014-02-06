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
package org.grouplens.lenskit.mf.funksvd;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import mikera.vectorz.AVector;
import mikera.vectorz.Vector;
import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.baseline.BaselineScorer;
import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.event.Ratings;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.data.pref.PreferenceDomain;
import org.grouplens.lenskit.iterative.TrainingLoopController;
import org.grouplens.lenskit.mf.svd.BiasedMFKernel;
import org.grouplens.lenskit.mf.svd.DomainClampingKernel;
import org.grouplens.lenskit.mf.svd.DotProductKernel;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Do recommendations and predictions based on SVD matrix factorization.
 *
 * Recommendation is done based on folding-in.  The strategy is do a fold-in
 * operation as described in
 * <a href="http://www.grouplens.org/node/212">Sarwar et al., 2002</a> with the
 * user's ratings.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class FunkSVDItemScorer extends AbstractItemScorer {

    protected final FunkSVDModel model;
    protected final BiasedMFKernel kernel;
    private UserEventDAO dao;
    private final ItemScorer baselineScorer;
    private final int featureCount;

    @Nullable
    private final FunkSVDUpdateRule rule;

    /**
     * Construct the item scorer.
     *
     * @param dao      The DAO.
     * @param model    The model.
     * @param baseline The baseline scorer.  Be very careful when configuring a different baseline
     *                 at runtime than at model-build time; such a configuration is unlikely to
     *                 perform well.
     * @param rule     The update rule, or {@code null} (the default) to only use the user features
     *                 from the model. If provided, this update rule is used to update a user's
     *                 feature values based on their profile when scores are requested.
     */
    @Inject
    public FunkSVDItemScorer(UserEventDAO dao, FunkSVDModel model,
                             @BaselineScorer ItemScorer baseline,
                             @Nullable PreferenceDomain dom,
                             @Nullable @RuntimeUpdate FunkSVDUpdateRule rule) {
        // FIXME Unify requirement on update rule and DAO
        this.dao = dao;
        this.model = model;
        baselineScorer = baseline;
        this.rule = rule;

        if (dom == null) {
            kernel = new DotProductKernel();
        } else {
            kernel = new DomainClampingKernel(dom);
        }

        featureCount = model.getFeatureCount();
    }

    @Nullable
    public FunkSVDUpdateRule getUpdateRule() {
        return rule;
    }

    /**
     * Predict for a user using their preference array and history vector.
     *
     * @param user   The user's ID
     * @param uprefs The user's preference vector.
     * @param output The output vector, whose key domain is the items to predict for. It must
     *               be initialized to the user's baseline predictions.
     */
    private void computeScores(long user, AVector uprefs, MutableSparseVector output) {
        for (VectorEntry e : output.fast()) {
            final long item = e.getKey();
            AVector ivec = model.getItemVector(item);
            if (ivec == null) {
                continue;
            }

            double score = kernel.apply(e.getValue(), uprefs, ivec);
            output.set(e, score);
        }
    }

    /**
     * Get estimates for all a user's ratings and the target items.
     *
     * @param user    The user ID.
     * @param ratings The user's ratings.
     * @param items   The target items.
     * @return Baseline predictions for all items either in the target set or the set of
     *         rated items.
     */
    private MutableSparseVector initialEstimates(long user, SparseVector ratings, LongSortedSet items) {
        LongSet allItems = LongUtils.setUnion(items, ratings.keySet());
        MutableSparseVector estimates = MutableSparseVector.create(allItems);
        baselineScorer.score(user, estimates);
        return estimates;
    }

    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
        UserHistory<Rating> history = dao.getEventsForUser(user, Rating.class);
        if (history == null) {
            history = History.forUser(user);
        }
        SparseVector ratings = Ratings.userRatingVector(history);

        MutableSparseVector estimates = initialEstimates(user, ratings, scores.keyDomain());
        // propagate estimates to the output scores
        scores.set(estimates);

        AVector uprefs = model.getUserVector(user);
        if (uprefs == null) {
            if (ratings.isEmpty()) {
                // no real work to do, stop with baseline predictions
                return;
            }
            uprefs = model.getAverageUserVector();
        }

        if (!ratings.isEmpty() && rule != null) {
            AVector updated = Vector.create(uprefs);
            for (int f = 0; f < featureCount; f++) {
                trainUserFeature(user, updated, ratings, estimates, f);
            }
            uprefs = updated;
        }

        // scores are the estimates, uprefs are trained up.
        computeScores(user, uprefs, scores);
    }

    private void trainUserFeature(long user, AVector uprefs, SparseVector ratings,
                                  MutableSparseVector estimates, int feature) {
        assert rule != null;
        assert uprefs.length() == featureCount;
        assert feature >= 0 && feature < featureCount;

        int tailStart = feature + 1;
        int tailSize = featureCount - feature - 1;
        AVector utail = uprefs.subVector(tailStart, tailSize);
        MutableSparseVector tails = MutableSparseVector.create(ratings.keySet());
        for (VectorEntry e: tails.fast(VectorEntry.State.EITHER)) {
            AVector ivec = model.getItemVector(e.getKey());
            if (ivec == null) {
                // FIXME Do this properly
                tails.set(e, 0);
            } else {
                ivec = ivec.subVector(tailStart, tailSize);
                tails.set(e, utail.dotProduct(ivec));
            }
        }

        double rmse = Double.MAX_VALUE;
        TrainingLoopController controller = rule.getTrainingLoopController();
        while (controller.keepTraining(rmse)) {
            rmse = doFeatureIteration(user, uprefs, ratings, estimates, feature, tails);
        }
    }

    private double doFeatureIteration(long user, AVector uprefs,
                                      SparseVector ratings, MutableSparseVector estimates,
                                      int feature, SparseVector itemTails) {
        assert rule != null;

        FunkSVDUpdater updater = rule.createUpdater();
        for (VectorEntry e: ratings.fast()) {
            final long iid = e.getKey();
            final AVector ivec = model.getItemVector(iid);
            if (ivec == null) {
                continue;
            }

            updater.prepare(feature, e.getValue(), estimates.get(iid),
                            uprefs.get(feature), ivec.get(feature), itemTails.get(iid));
            // Step 4: update user preferences
            uprefs.addAt(feature, updater.getUserFeatureUpdate());
        }
        return updater.getRMSE();
    }
}
