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
package org.grouplens.lenskit.core;

import com.google.common.base.Preconditions;
import org.grouplens.grapht.Component;
import org.grouplens.grapht.Dependency;
import org.grouplens.grapht.graph.DAGNode;
import org.grouplens.grapht.solver.DependencySolver;
import org.grouplens.grapht.solver.SolverException;
import org.grouplens.lenskit.RecommenderBuildException;
import org.grouplens.lenskit.RecommenderEngine;
import org.grouplens.lenskit.inject.GraphtUtils;
import org.grouplens.lenskit.inject.RecommenderGraphBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.*;

/**
 * LensKit implementation of a recommender engine.  It uses containers set up by
 * the {@link LenskitConfiguration} to set up actual recommenders, and can build
 * multiple recommenders from the same model.
 * <p>If you just want to quick create a recommender for evaluation or testing,
 * consider using {@link LenskitRecommender#build(LenskitConfiguration)}.</p>
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 * @compat Public
 * @see LenskitConfiguration
 * @see LenskitRecommender
 */
public final class LenskitRecommenderEngine implements RecommenderEngine {
    private static final Logger logger = LoggerFactory.getLogger(LenskitRecommenderEngine.class);

    private final DAGNode<Component, Dependency> graph;
    private final boolean instantiable;

    LenskitRecommenderEngine(DAGNode<Component,Dependency> dependencies,
                             boolean instantiable) {
        this.graph = dependencies;
        this.instantiable = instantiable;
    }

    /**
     * Create a new LenskitRecommenderEngine by reading a previously serialized engine from the
     * given file. The new engine will be identical to the old except it will use the new
     * DAOFactory. It is assumed that the file was created by using {@link #write(OutputStream)}.
     * Classes will be loaded using a default class loader.
     *
     * @param file The file from which to load the engine.
     * @return The loaded recommender engine.
     * @throws IOException If there is an error reading from the file.
     * @throws RecommenderConfigurationException
     *                     If the configuration cannot be used.
     */
    public static LenskitRecommenderEngine load(File file) throws IOException, RecommenderConfigurationException {
        return newLoader().load(file);
    }

    /**
     * Create a new LenskitRecommenderEngine by reading a previously serialized engine from the
     * given file. The new engine will be identical to the old except it will use the new
     * DAOFactory. It is assumed that the file was created by using {@link #write(OutputStream)}.
     *
     * @param file   The file from which to load the engine.
     * @param loader The class loader to load from ({@code null} to use a default class loader).
     * @return The loaded recommender engine.
     * @throws IOException If there is an error reading from the file.
     * @throws RecommenderConfigurationException
     *                     If the configuration cannot be used.
     * @deprecated Use {@link LenskitRecommenderEngineLoader} for sophisticated loading.
     */
    @Deprecated
    public static LenskitRecommenderEngine load(File file, ClassLoader loader) throws IOException, RecommenderConfigurationException {
        return newLoader().setClassLoader(loader).load(file);
    }

    /**
     * Create a new LenskitRecommenderEngine by reading a previously serialized engine from the
     * given input stream. The new engine will be identical to the old. It is assumed that the file
     * was created by using {@link #write(OutputStream)}.  Classes will be loaded using a default
     * class loader.
     *
     * @param input The stream from which to load the engine.
     * @return The loaded recommender engine.
     * @throws IOException If there is an error reading from the file.
     * @throws RecommenderConfigurationException
     *                     If the configuration cannot be used.
     */
    public static LenskitRecommenderEngine load(InputStream input) throws IOException, RecommenderConfigurationException {
        return newLoader().load(input);
    }

    /**
     * Create a new LenskitRecommenderEngine by reading a previously serialized engine from the
     * given input stream. The new engine will be identical to the old. It is assumed that the file
     * was created by using {@link #write(OutputStream)}.
     *
     * @param input  The stream from which to load the engine.
     * @param loader The class loader to load from ({@code null} to use a default class loader).
     * @return The loaded recommender engine.
     * @throws IOException If there is an error reading from the file.
     * @throws RecommenderConfigurationException
     *                     If the configuration cannot be used.
     * @deprecated Use {@link LenskitRecommenderEngineLoader} for sophisticated loading.
     */
    public static LenskitRecommenderEngine load(InputStream input, ClassLoader loader) throws IOException, RecommenderConfigurationException {
        return newLoader().setClassLoader(loader).load(input);
    }

    /**
     * Write the state of this LenskitRecommenderEngine to the given file so
     * that it can be recreated later using another DAOFactory. This uses
     * default object serialization so if the factory has a PicoContainer or
     * session bindings containing non-serializable types, this will fail.
     *
     * @param file The file to write the rec engine to.
     * @throws IOException if there is an error serializing the engine.
     * @see #write(java.io.OutputStream)
     */
    public void write(@Nonnull File file) throws IOException {
        OutputStream out = new FileOutputStream(file);
        try {
            write(out);
        } finally {
            out.close();
        }
    }

    /**
     * Write the state of this LenskitRecommenderEngine to the given stream so
     * that it can be recreated later using another DAOFactory. This uses
     * default object serialization so if the factory has session bindings
     * containing non-serializable types, this will fail.
     *
     * @param stream The file to write the rec engine to.
     * @throws IOException if there is an error serializing the engine.
     * @see #load(InputStream)
     */
    public void write(@Nonnull OutputStream stream) throws IOException {
        ObjectOutputStream out = new ObjectOutputStream(stream);
        try {
            out.writeObject(graph);
        } finally {
            out.close();
        }
    }

    @Override
    public LenskitRecommender createRecommender() {
        Preconditions.checkState(instantiable, "recommender engine does not have instantiable graph");
        return new LenskitRecommender(graph);
    }

    /**
     * Construct a recommender with some additional configuration.  This can be used to do things
     * like add data source configuration on a per-recommender, rather than per-engine, basis.
     *
     * @param config The configuration to adjust the recommender.
     * @return The constructed recommender.
     * @throws RecommenderConfigurationException if there is an error configuring the recommender.
     */
    public LenskitRecommender createRecommender(LenskitConfiguration config) throws RecommenderConfigurationException {
        Preconditions.checkNotNull(config, "extra configuration");
        final DAGNode<Component, Dependency> toBuild;
        RecommenderGraphBuilder rgb = new RecommenderGraphBuilder();
        rgb.addBindings(config.getBindings());
        DependencySolver solver = rgb.buildDependencySolver();
        try {
            toBuild = solver.rewrite(graph);
        } catch (SolverException ex) {
            throw new RecommenderConfigurationException("error reconfiguring recommender", ex);
        }
        GraphtUtils.checkForPlaceholders(toBuild, logger);

        return new LenskitRecommender(toBuild);
    }

    /**
     * Query whether this engine is instantiable.  Instantiable recommenders have all their
     * placeholders removed and are ready to instantiate.
     * @return {@code true} if the recommender is instantiable.
     */
    public boolean isInstantiable() {
        return instantiable;
    }

    /**
     * Get the dependency graph of the recommender engine.
     *
     * @return The dependency graph.
     */
    DAGNode<Component, Dependency> getGraph() {
        return graph;
    }

    /**
     * Build a LensKit recommender engine from a configuration.  The resulting recommender is
     * independent of any subsequent modifications to the configuration.  The recommender is built
     * without a symbol mapping.
     *
     * @param config     The configuration.
     * @return The recommender engine.
     */
    public static LenskitRecommenderEngine build(LenskitConfiguration config) throws RecommenderBuildException {
        return newBuilder().addConfiguration(config).build();
    }

    /**
     * Create a new recommender engine builder.
     * @return A new recommender engine builder.
     */
    public static LenskitRecommenderEngineBuilder newBuilder() {
        return new LenskitRecommenderEngineBuilder();
    }

    /**
     * Create a new recommender engine loader.
     * @return A new recommender engine loader.
     */
    public static LenskitRecommenderEngineLoader newLoader() {
        return new LenskitRecommenderEngineLoader();
    }
}
