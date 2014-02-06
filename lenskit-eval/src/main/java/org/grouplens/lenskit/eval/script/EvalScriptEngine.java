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
package org.grouplens.lenskit.eval.script;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import groovy.lang.Binding;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.MissingPropertyException;
import org.apache.commons.lang3.builder.Builder;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.grouplens.lenskit.util.ClassDirectory;
import org.grouplens.lenskit.eval.EvalProject;
import org.grouplens.lenskit.eval.EvalTask;
import org.grouplens.lenskit.eval.TaskExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.*;

/**
 * Load and process configuration files. Also provides helper methods used by the
 * configuration scripts to locate & invoke methods.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 * @since 0.10
 */
public class EvalScriptEngine {
    private static Logger logger = LoggerFactory.getLogger(EvalScriptEngine.class);
    private static final String METHOD_PATH = "META-INF/lenskit-eval/methods/";

    protected ClassLoader classLoader;
    protected ClassDirectory directory;
    protected GroovyShell shell;
    @Nullable
    protected final Properties properties;

    @SuppressWarnings("rawtypes")
    private final Map<Class, Class> builders = new HashMap<Class, Class>();

    /**
     * Construct a new script engine. The engine uses the current thread's classloader.
     */
    public EvalScriptEngine() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Construct a new script engine.
     * @param loader The class loader to use.
     */
    public EvalScriptEngine(ClassLoader loader) {
        this(loader, null);
    }

    /**
     * Construct a new script engine.
     * @param loader The class loader to use.
     * @param props Additional properties to use when creating new projects.
     * @see org.grouplens.lenskit.eval.EvalProject#EvalProject(java.util.Properties)
     */
    public EvalScriptEngine(ClassLoader loader, @Nullable Properties props) {
        CompilerConfiguration compConfig = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
        properties = props;

        compConfig.setScriptBaseClass("org.grouplens.lenskit.eval.script.EvalScript");

        ImportCustomizer imports = new ImportCustomizer();
        imports.addStarImports("org.grouplens.lenskit",
                               "org.grouplens.lenskit.params",
                               "org.grouplens.lenskit.baseline",
                               "org.grouplens.lenskit.norm",
                               "org.grouplens.lenskit.eval.metrics.predict",
                               "org.grouplens.lenskit.eval.metrics.recommend");
        compConfig.addCompilationCustomizers(imports);
        shell = new GroovyShell(loader, new Binding(), compConfig);
        classLoader = loader;

        loadExternalMethods();

        directory = ClassDirectory.forClassLoader(loader);
    }

    /**
     * Create a new eval project.
     * @return The eval project.
     */
    public EvalProject createProject() {
        return new EvalProject(properties);
    }

    //region Loading and running scripts
    /**
     * Run a script from a file.
     *
     * @param file The file to run.
     * @param project The project to run the script against.
     * @return The script as parsed and compiled by Groovy.
     * @throws IOException if the file cannot be read.
     */
    public Object runScript(File file, EvalProject project) throws IOException, TaskExecutionException {
        EvalScript script = (EvalScript) shell.parse(file);
        return runScript(script, project);
    }

    /**
     * Run a script from a reader.
     *
     * @param in The reader to read.
     * @param project The project to run the script against.
     * @return The script as parsed and compiled by Groovy.
     */
    public Object runScript(Reader in, EvalProject project) throws IOException {
        EvalScript script;
        try {
            script =  (EvalScript) shell.parse(in);
        } catch (GroovyRuntimeException e) {
            if (e.getCause() != null) {
                Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
            }
            throw e;
        }
        return script;
    }

    /**
     * Run an evaluation config script and get the evaluations it produces.
     *
     * @param script The script to run (as loaded by Groovy)
     * @param project The project to run the script on.
     * @return The return value of the script.
     * @throws org.grouplens.lenskit.eval.TaskExecutionException if the script is invalid or produces an error.
     */
    @Nullable
    public Object runScript(EvalScript script, EvalProject project) throws TaskExecutionException {
        script.setEngine(this);
        script.setProject(project);
        Object result = null;
        try {
            result = script.run();
        } catch (MissingPropertyException e) {
            String name = e.getProperty();
            Set<String> packages = directory.getPackages(name);
            logger.error("Cannot resolve class or property " + name);
            if (!packages.isEmpty()) {
                logger.info("Did you intend to import it from {}?", Joiner.on(", ").join(packages));
            }
            throw new TaskExecutionException("unresolvable property " + name, e);
        } catch (RuntimeException e) {
            Throwables.propagateIfInstanceOf(e.getCause(), TaskExecutionException.class);
            throw new TaskExecutionException("error running configuration script", e);
        }
        return result;
    }

    /**
     * Load a set of evaluations from a script file.
     *
     * @param file A Groovy script to configure the evaluator.
     * @return A list of evaluations to run.
     * @throws org.grouplens.lenskit.eval.TaskExecutionException if there is a configuration error
     * @throws IOException      if there is an error reading the file
     */
    public EvalProject loadProject(File file) throws TaskExecutionException, IOException {
        logger.debug("loading script file {}", file);
        EvalProject project = new EvalProject(properties);
        runScript(file, project);
        return project;
    }

    /**
     * Load a set of evaluations from an input stream.
     *
     * @param in The input stream
     * @return A list of evaluations
     * @throws org.grouplens.lenskit.eval.TaskExecutionException if there is a configuration error
     */
    public Object loadProject(Reader in) throws TaskExecutionException, IOException {
        EvalProject project = createProject();
        runScript(in, project);
        return project;
    }
    //endregion

    //region External method lookup
    private <R> Class<? extends R> lookupMethod(Class<R> root, String key, String name) {
        // FIXME Cache these lookups
        String path = METHOD_PATH + name + ".properties";
        logger.debug("loading method {} from {}", name, path);

        try {
            InputStream istr = classLoader.getResourceAsStream(path);
            if (istr == null) {
                logger.debug("path {} not found", path);
                return null;
            }
            try {
                Properties props = new Properties();
                props.load(istr);
                Object pv = props.get(key);
                String className = pv == null ? null : pv.toString();
                if (className == null) {
                    return null;
                }

                return classLoader.loadClass(className).asSubclass(root);
            } finally {
                istr.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("error reading method " + name, e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("cannot find command class", e);
        }
    }

    /**
     * Look up a registered method of any type.  The currently supported types are {@linkplain Builder builders}
     * and {@linkplain org.grouplens.lenskit.eval.EvalTask tasks}.
     * @param name The method name.
     * @return The method implementation class, or {@code null} if it the method is not found.
     */
    public Class<?> lookupMethod(@Nonnull String name) {
        Class<?> task = lookupTask(name);
        Class<?> builder = lookupBuilder(name);
        if (task == null && builder == null) {
            return null;
        } else if (task != null) {
            if (builder == null) {
                return task;
            } else {
                throw new RuntimeException("ambiguous method " + name);
            }
        } else {
            return builder;
        }
    }

    /**
     * Find a task with a particular name if it exists.
     *
     * @param name The name of the command
     * @return The command factory or {@code null} if no such factory exists.
     */
    @SuppressWarnings("rawtypes")
    @CheckForNull
    @Nullable
    public Class<? extends EvalTask> lookupTask(@Nonnull String name) {
        return lookupMethod(EvalTask.class, "task", name);
    }

    /**
     * Find a builder with a particular name if it exists.
     *
     * @param name The name of the command
     * @return The command factory or {@code null} if no such factory exists.
     */
    @SuppressWarnings("rawtypes")
    @CheckForNull
    @Nullable
    public Class<? extends Builder> lookupBuilder(@Nonnull String name) {
        return lookupMethod(Builder.class, "builder", name);
    }

    /**
     * Get a command for a type. It consults registered commands and looks for the
     * {@link BuiltBy} annotation.
     *
     * @param type A type that needs to be built.
     * @return A command class to build {@code type}, or {@code null} if none can be found.
     * @see #registerCommand
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Class<? extends Builder> getBuilderForType(Class<T> type) {
        @SuppressWarnings("rawtypes")
        Class builder = builders.get(type);
        if (builder == null) {
            BuiltBy annot = type.getAnnotation(BuiltBy.class);
            if (annot != null) {
                builder = annot.value();
            }
        }
        return builder;
    }

    /**
     * Register a builder class for a type. Used to allow commands to be found for types where
     * the type cannot be augmented with the {@link BuiltBy} annotation.
     *
     * @param type    The type to build.
     * @param command A class that can build instances of {@code type}.
     * @param <T>     The type to build (type parameter).
     */
    @SuppressWarnings("rawtypes")
    public <T> void registerCommand(Class<T> type, Class<? extends Builder> command) {
        Preconditions.checkNotNull(type, "type cannot be null");
        Preconditions.checkNotNull(command, "command cannot be null");
        builders.put(type, command);
    }

    /**
     * Register a default set of external methods.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void loadExternalMethods() {
        Properties props = new Properties();
        try {
            for (URL url : Collections.list(classLoader.getResources("META-INF/lenskit-eval/builders.properties"))) {
                InputStream istr = url.openStream();
                try {
                    props.load(istr);
                } finally {
                    istr.close();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (Map.Entry<Object, Object> prop : props.entrySet()) {
            String name = prop.getKey().toString();
            String command = prop.getValue().toString();
            Class cls;
            try {
                cls = classLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                logger.warn("command registered for nonexistent class {}", name);
                continue;
            }
            Class cmd;
            try {
                cmd = Class.forName(command).asSubclass(Builder.class);
            } catch (ClassNotFoundException e) {
                logger.error("command class {} not builder", command);
                continue;
            } catch (ClassCastException e) {
                logger.error("class {} is not a builder", command);
                continue;
            }
            logger.debug("registering {} as builder for {}", command, cls);
            registerCommand(cls, cmd);
        }
    }
    //endregion
}
