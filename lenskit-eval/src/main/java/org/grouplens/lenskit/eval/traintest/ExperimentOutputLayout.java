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
package org.grouplens.lenskit.eval.traintest;

import org.apache.commons.lang3.tuple.Pair;
import org.grouplens.lenskit.eval.Attributed;
import org.grouplens.lenskit.eval.data.traintest.TTDataSet;
import org.grouplens.lenskit.eval.metrics.TestUserMetric;
import org.grouplens.lenskit.symbols.Symbol;
import org.grouplens.lenskit.util.table.TableLayout;
import org.grouplens.lenskit.util.table.TableLayoutBuilder;
import org.grouplens.lenskit.util.table.writer.TableWriter;
import org.grouplens.lenskit.util.table.writer.TableWriters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Layouts for experiment output tables.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
class ExperimentOutputLayout {
    private final TableLayout commonLayout;
    private Map<String, Integer> dataColumns;
    private Map<String, Integer> algoColumns;
    private final TableLayout resultsLayout;
    private final TableLayout userLayout;
    private final TableLayout predictLayout;
    private final TableLayout recommendLayout;

    public ExperimentOutputLayout(TableLayout common,
                                  Map<String, Integer> algoCols,
                                  Map<String, Integer> dataCols,
                                  TableLayout results, TableLayout user,
                                  TableLayout predict, TableLayout rec) {
        commonLayout = common;
        dataColumns = algoCols;
        algoColumns = dataCols;
        resultsLayout = results;
        userLayout = user;
        predictLayout = predict;
        recommendLayout = rec;
    }

    public TableLayout getCommonLayout() {
        return commonLayout;
    }

    public int getCommonColumnCount() {
        return commonLayout.getColumnCount();
    }

    public int getDataColumn(String name) {
        Integer idx = dataColumns.get(name);
        if (idx == null) {
            throw new IllegalArgumentException("no such data column " + name);
        } else {
            return idx;
        }
    }

    public int getAlgorithmColumn(String name) {
        Integer idx = algoColumns.get(name);
        if (idx == null) {
            throw new IllegalArgumentException("no such algorithm column " + name);
        } else {
            return idx;
        }
    }

    public TableLayout getResultsLayout() {
        return resultsLayout;
    }

    public TableLayout getUserLayout() {
        return userLayout;
    }

    public TableLayout getPredictLayout() {
        return predictLayout;
    }

    public TableLayout getRecommendLayout() {
        return recommendLayout;
    }

    /**
     * Prefix a table for a particular algorithmInfo and data set.
     *
     *
     *
     * @param base      The table to prefix.
     * @param algorithm The algorithmInfo to prefix for.
     * @param dataSet   The data set to prefix for.
     * @return A prefixed table, suitable for outputting the results of evaluating
     *         {@code algorithmInfo} on {@code dataSet}, or {@code null} if {@code base} is null.
     */
    public TableWriter prefixTable(TableWriter base, Attributed algorithm, TTDataSet dataSet) {
        if (base == null) {
            return null;
        }

        Object[] prefix = new Object[getCommonColumnCount()];
        prefix[0] = algorithm.getName();
        for (Map.Entry<String, Object> attr : dataSet.getAttributes().entrySet()) {
            int idx = getDataColumn(attr.getKey());
            prefix[idx] = attr.getValue();
        }
        for (Map.Entry<String, Object> attr : algorithm.getAttributes().entrySet()) {
            int idx = getAlgorithmColumn(attr.getKey());
            prefix[idx] = attr.getValue();
        }
        return TableWriters.prefixed(base, prefix);
    }

    /**
     * Create an experiment output layout.
     * @param experiments The experiments.
     * @param measurements The measurements.
     * @return An output layout for the suite of experiments.
     */
    public static ExperimentOutputLayout create(ExperimentSuite experiments, MeasurementSuite measurements) {
        TableLayoutBuilder master = new TableLayoutBuilder();
        master.addColumn("Algorithm");
        Map<String,Integer> dataColumns = new HashMap<String, Integer>();
        for (String attr: experiments.getDataAttributes()) {
            dataColumns.put(attr, master.getColumnCount());
            master.addColumn(attr);
        }

        Map<String,Integer> algoColumns = new HashMap<String, Integer>();
        for (String attr: experiments.getAlgorithmAttributes()) {
            algoColumns.put(attr, master.getColumnCount());
            master.addColumn(attr);
        }

        TableLayout common = master.build();

        TableLayout results = layoutAggregateOutput(master, measurements);
        TableLayout user = layoutUserTable(master, measurements);
        TableLayout predict = layoutPredictionTable(master, measurements);
        TableLayout recommend = layoutRecommendTable(master);

        return new ExperimentOutputLayout(common, dataColumns, algoColumns,
                                          results, user, predict, recommend);
    }

    private static TableLayout layoutAggregateOutput(TableLayoutBuilder master, MeasurementSuite measurements) {
        TableLayoutBuilder output = master.clone();
        output.addColumn("BuildTime");
        output.addColumn("TestTime");

        for (ModelMetric ev: measurements.getModelMetrics()) {
            for (String c: ev.getColumnLabels()) {
                output.addColumn(c);
            }
        }

        for (TestUserMetric ev : measurements.getTestUserMetrics()) {
            List<String> columnLabels = ev.getColumnLabels();
            if (columnLabels != null) {
                for (String c : columnLabels) {
                    output.addColumn(c);
                }
            }
        }

        return output.build();
    }

    private static TableLayout layoutUserTable(TableLayoutBuilder master, MeasurementSuite measurements) {
        TableLayoutBuilder perUser = master.clone();
        perUser.addColumn("User");

        for (TestUserMetric ev : measurements.getTestUserMetrics()) {
            List<String> userColumnLabels = ev.getUserColumnLabels();
            if (userColumnLabels != null) {
                for (String c : userColumnLabels) {
                    perUser.addColumn(c);
                }
            }
        }

        return perUser.build();
    }

    private static TableLayout layoutPredictionTable(TableLayoutBuilder master, MeasurementSuite measurements) {
        TableLayoutBuilder eachPred = master.clone();
        eachPred.addColumn("User");
        eachPred.addColumn("Item");
        eachPred.addColumn("Rating");
        eachPred.addColumn("Prediction");
        for (Pair<Symbol,String> pair: measurements.getPredictionChannels()) {
            eachPred.addColumn(pair.getRight());
        }

        return eachPred.build();
    }

    private static TableLayout layoutRecommendTable(TableLayoutBuilder master) {
        TableLayoutBuilder eachReco = master.clone();
        eachReco.addColumn("User");
        eachReco.addColumn("Item");
        eachReco.addColumn("Ranking");
        eachReco.addColumn("Prediction");

        return eachReco.build();
    }
}
