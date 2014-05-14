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
package org.grouplens.lenskit.cli;

import com.google.common.base.Stopwatch;
import com.google.common.io.Closer;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.grouplens.lenskit.ItemRecommender;
import org.grouplens.lenskit.RecommenderBuildException;
import org.grouplens.lenskit.core.*;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.symbols.Symbol;
import org.grouplens.lenskit.util.io.LKFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Generate Top-N recommendations for users.
 *
 * @since 2.1
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
@CommandSpec(name="recommend", help="generate recommendations for users")
public class Recommend implements Command {
    private final Logger logger = LoggerFactory.getLogger(Recommend.class);
    private final Namespace options;
    private final InputData input;
    private final ScriptEnvironment environment;

    public Recommend(Namespace opts) {
        options = opts;
        input = new InputData(opts);
        environment = new ScriptEnvironment(opts);
    }

    @Override
    public void execute() throws IOException, RecommenderBuildException {
        LenskitRecommenderEngine engine = loadEngine();

        List<Long> users = options.get("users");
        final int n = options.getInt("num_recs");

        LenskitRecommender rec = engine.createRecommender();
        ItemRecommender irec = rec.getItemRecommender();
        if (irec == null) {
            logger.error("recommender has no item recommender");
            throw new UnsupportedOperationException("no item recommender");
        }

        logger.info("recommending for {} users", users.size());
        Symbol pchan = getPrintChannel();
        Stopwatch timer = new Stopwatch();
        timer.start();
        File output = options.get("output_file");
        Set<Long> candidates = getCandidates();
        if(output != null) {
            logger.info("writing recommendations to file {}", output);
            Closer closer = Closer.create();
            try {
                OutputStream stream = closer.register(new FileOutputStream(output));
                if (LKFileUtils.isCompressed(output)) {
                    stream = closer.register(new GZIPOutputStream(stream));
                }
                for (long user: users) {
                    List<ScoredId> recs;
                    if(candidates!=null){
                        recs = irec.recommend(user, n, candidates, null);
                    } else {
                        recs = irec.recommend(user, n);
                    }
                    System.out.format("recommendations for user %d:\n", user);
                    for (ScoredId item: recs) {
                        System.out.format("  %d: %.3f", item.getId(), item.getScore());
                        String line = String.format("%d, %d, %.3f\n", user, item.getId(), item.getScore());
                        stream.write(line.getBytes());
                        if (pchan != null && item.hasUnboxedChannel(pchan)) {
                            System.out.format(" (%f)", item.getUnboxedChannelValue(pchan));
                        }
                        System.out.println();
                    }
                }
            } catch (Throwable th) {
                throw closer.rethrow(th);
            } finally {
                closer.close();
            }
        } else {
            for (long user : users) {
                List<ScoredId> recs;
                if(candidates!=null){
                    recs = irec.recommend(user, n, candidates, null);
                } else {
                    recs = irec.recommend(user, n);
                }
                System.out.format("recommendations for user %d:\n", user);
                for (ScoredId item : recs) {
                    System.out.format("  %d: %.3f", item.getId(), item.getScore());
                    if (pchan != null && item.hasUnboxedChannel(pchan)) {
                        System.out.format(" (%f)", item.getUnboxedChannelValue(pchan));
                    }
                    System.out.println();
                }
            }
        }
        timer.stop();
        logger.info("recommended for {} users in {}", users.size(), timer);
    }


    private LenskitRecommenderEngine loadEngine() throws RecommenderBuildException, IOException {
        File modelFile = options.get("model_file");
        if (modelFile == null) {
            logger.info("creating fresh recommender");
            LenskitRecommenderEngineBuilder builder = LenskitRecommenderEngine.newBuilder();
            for (LenskitConfiguration config: environment.loadConfigurations(getConfigFiles())) {
                builder.addConfiguration(config);
            }
            builder.addConfiguration(input.getConfiguration());
            Stopwatch timer = new Stopwatch();
            timer.start();
            LenskitRecommenderEngine engine = builder.build();
            timer.stop();
            logger.info("built recommender in {}", timer);
            return engine;
        } else {
            logger.info("loading recommender from {}", modelFile);
            LenskitRecommenderEngineLoader loader = LenskitRecommenderEngine.newLoader();
            for (LenskitConfiguration config: environment.loadConfigurations(getConfigFiles())) {
                loader.addConfiguration(config);
            }
            loader.addConfiguration(input.getConfiguration());
            Stopwatch timer = new Stopwatch();
            timer.start();
            LenskitRecommenderEngine engine;
            InputStream input = new FileInputStream(modelFile);
            try {
                if (LKFileUtils.isCompressed(modelFile)) {
                    input = new GZIPInputStream(input);
                }
                engine = loader.load(input);
            } finally {
                input.close();
            }
            timer.stop();
            logger.info("loaded recommender in {}", timer);
            return engine;
        }
    }

    List<File> getConfigFiles() {
        return options.getList("config_file");
    }

    Symbol getPrintChannel() {
        String name = options.get("print_channel");
        if (name == null) {
            return null;
        } else {
            return Symbol.of(name);
        }
    }

    Set<Long> getCandidates() throws IOException {
        File f = options.get("candidate");
        Path file = f.toPath();
        if(file == null) {
            return null;
        }
        List<String> lines = Files.readAllLines(file, Charset.defaultCharset());
        Set<Long> candidates = new HashSet<Long>();
        for(String id : lines) {
            candidates.add(Long.parseLong(id));
        }
        return candidates;
    }

    public static void configureArguments(ArgumentParser parser) {
        InputData.configureArguments(parser);
        ScriptEnvironment.configureArguments(parser);
        parser.addArgument("-n", "--num-recs")
              .type(Integer.class)
              .setDefault(10)
              .metavar("N")
              .help("generate up to N recommendations per user");
        parser.addArgument("-c", "--config-file")
              .type(File.class)
              .action(Arguments.append())
              .metavar("FILE")
              .help("use configuration from FILE");
        parser.addArgument("-o", "--output-file")
                .type(File.class)
                .metavar("FILE")
                .help("the output file of the recommendation results");
        parser.addArgument("--candidate")
                .type(File.class)
                .metavar("FILE")
                .help("the candidate movie ids for recommendation");
        parser.addArgument("-m", "--model-file")
              .type(File.class)
              .metavar("FILE")
              .help("load model from FILE");
        parser.addArgument("--print-channel")
              .metavar("CHAN")
              .help("also print value from CHAN");
        parser.addArgument("users")
              .type(Long.class)
              .nargs("+")
              .metavar("USER")
              .help("recommend for USERS");
    }
}
