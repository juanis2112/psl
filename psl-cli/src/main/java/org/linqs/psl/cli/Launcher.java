/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.cli;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Options;
import org.linqs.psl.config.RuntimeOptions;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.runtime.Runtime;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.Reflection;
import org.linqs.psl.util.Version;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Launches PSL from the command line.
 * Supports inference and supervised parameter learning.
 */
public class Launcher {
    private static final Logger log = Logger.getLogger(Launcher.class);
    private CommandLine parsedOptions;

    private Launcher(CommandLine givenOptions) {
        this.parsedOptions = givenOptions;
    }

    private void outputServerResponses(List<OnlineResponse> serverResponses) {
        for (OnlineResponse response : serverResponses) {
            System.out.println(response.toString());
        }
    }

    private void outputServerResponses(List<OnlineResponse> serverResponses, String outputFilePath) {
        Path outputDirectory = Paths.get(outputFilePath).getParent();
        if (outputDirectory != null) {
            FileUtils.mkdir(outputDirectory.toString());
        }

        try (BufferedWriter bufferedWriter = FileUtils.getBufferedWriter(outputFilePath)) {
            for (OnlineResponse response : serverResponses) {
                bufferedWriter.write(response.toString() + "\n");
            }
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Error writing online server responses to file: %s", outputFilePath), ex);
        }
    }

    private void runOnlineClient() {
        log.info("Starting OnlinePSL client.");
        List<OnlineResponse> serverResponses = OnlineActionInterface.run();
        log.info("OnlinePSL client closed.");

        // Output the results.
        if (!(parsedOptions.hasOption(CommandLineLoader.OPTION_ONLINE_SERVER_RESPONSE_OUTPUT))) {
            log.trace("Writing server responses to stdout.");
            outputServerResponses(serverResponses);
        } else {
            String outputFilePath = parsedOptions.getOptionValue(CommandLineLoader.OPTION_ONLINE_SERVER_RESPONSE_OUTPUT);
            log.trace("Writing inferred predicates to file: " + outputFilePath);
            outputServerResponses(serverResponses, outputFilePath);
        }
    }

    /**
     * Convert all compatible options to the PSL runtime.
     */
    private void convertRuntimeOptions() {
        if (parsedOptions.hasOption(CommandLineLoader.OPERATION_INFER)) {
            RuntimeOptions.INFERENCE.set(true);
            RuntimeOptions.INFERENCE_METHOD.set(parsedOptions.getOptionValue(CommandLineLoader.OPERATION_INFER));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPERATION_LEARN)) {
            RuntimeOptions.LEARN.set(true);
            RuntimeOptions.LEARN_METHOD.set(parsedOptions.getOptionValue(CommandLineLoader.OPERATION_LEARN));
        }

        // HACK(eriq): Since the CLU currently only supports one mode (infer/learn) at a time,
        // we will just set both modes when we see data/model files.

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_DATA)) {
            RuntimeOptions.INFERENCE_DATA_PATH.set(parsedOptions.getOptionValue(CommandLineLoader.OPTION_DATA));
            RuntimeOptions.LEARN_DATA_PATH.set(parsedOptions.getOptionValue(CommandLineLoader.OPTION_DATA));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_MODEL)) {
            RuntimeOptions.INFERENCE_MODEL_PATH.set(parsedOptions.getOptionValue(CommandLineLoader.OPTION_MODEL));
            RuntimeOptions.LEARN_MODEL_PATH.set(parsedOptions.getOptionValue(CommandLineLoader.OPTION_MODEL));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_DB_H2_PATH)) {
            RuntimeOptions.DB_H2.set(true);
            RuntimeOptions.DB_H2_PATH.set(parsedOptions.getOptionValue(CommandLineLoader.OPTION_DB_H2_PATH));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_DB_POSTGRESQL_NAME)) {
            RuntimeOptions.DB_PG.set(true);
            RuntimeOptions.DB_PG_NAME.set(parsedOptions.getOptionValue(CommandLineLoader.OPTION_DB_POSTGRESQL_NAME));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_EVAL)) {
            List<String> evaluatorNames = new ArrayList<String>();
            for (String evaluatorName : parsedOptions.getOptionValues(CommandLineLoader.OPTION_EVAL)) {
                evaluatorNames.add(evaluatorName);
            }

            RuntimeOptions.INFERENCE_EVAL.set(ListUtils.join(",", evaluatorNames));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_INT_IDS)) {
            RuntimeOptions.DB_INT_IDS.set(parsedOptions.hasOption(CommandLineLoader.OPTION_INT_IDS));
        }

        // Look specially for the logging level.
        if (parsedOptions.hasOption(CommandLineLoader.OPTION_PROPERTIES)) {
            Properties props = parsedOptions.getOptionProperties(CommandLineLoader.OPTION_PROPERTIES);
            if (props.containsKey("log4j.threshold")) {
                RuntimeOptions.LOG_LEVEL.set(props.getProperty("log4j.threshold"));
            }
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_OUTPUT_DIR)) {
            RuntimeOptions.INFERENCE_OUTPUT_RESULTS_DIR.set(parsedOptions.getOptionValue(CommandLineLoader.OPTION_OUTPUT_DIR));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_OUTPUT_GROUND_RULES_LONG)) {
            RuntimeOptions.INFERENCE_OUTPUT_GROUNDRULES.set(true);

            String path = parsedOptions.getOptionValue(CommandLineLoader.OPTION_OUTPUT_GROUND_RULES_LONG);
            if (path != null) {
                RuntimeOptions.INFERENCE_OUTPUT_GROUNDRULES_PATH.set(path);
            }
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_OUTPUT_SATISFACTION_LONG)) {
            RuntimeOptions.INFERENCE_OUTPUT_SATISFACTIONS.set(true);

            String path = parsedOptions.getOptionValue(CommandLineLoader.OPTION_OUTPUT_SATISFACTION_LONG);
            if (path != null) {
                RuntimeOptions.INFERENCE_OUTPUT_SATISFACTIONS_PATH.set(path);
            }
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_PROPERTIES_FILE)) {
            RuntimeOptions.PROPERTIES_PATH.set(parsedOptions.getOptionValue(CommandLineLoader.OPTION_PROPERTIES_FILE));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_SKIP_ATOM_COMMIT_LONG)) {
            RuntimeOptions.INFERENCE_COMMIT.set(!parsedOptions.hasOption(CommandLineLoader.OPTION_SKIP_ATOM_COMMIT_LONG));
        }
    }

    private void run() {
        convertRuntimeOptions();
        Runtime runtime = new Runtime();

        if (parsedOptions.hasOption(CommandLineLoader.OPERATION_ONLINE_CLIENT_LONG)) {
            runOnlineClient();
            return;
        }

        runtime.run();
    }

    private static boolean isCommandLineValid(CommandLine givenOptions) {
        // Return early in case of help or version option.
        if (givenOptions.hasOption(CommandLineLoader.OPTION_HELP) ||
                givenOptions.hasOption(CommandLineLoader.OPTION_VERSION)) {
            return false;
        }

        if (givenOptions.hasOption(CommandLineLoader.OPERATION_ONLINE_CLIENT_LONG)) {
            return true;
        }

        // Data and model are required for non-online PSL runs.
        // (We don't enforce them earlier so we can have successful runs with help and version.)
        HelpFormatter helpFormatter = new HelpFormatter();
        if (!givenOptions.hasOption(CommandLineLoader.OPTION_DATA)) {
            System.out.println(String.format("Missing required option: --%s/-%s.", CommandLineLoader.OPTION_DATA_LONG, CommandLineLoader.OPTION_DATA));
            helpFormatter.printHelp("psl", CommandLineLoader.getOptions(), true);
            return false;
        }
        if (!givenOptions.hasOption(CommandLineLoader.OPTION_MODEL)) {
            System.out.println(String.format("Missing required option: --%s/-%s.", CommandLineLoader.OPTION_MODEL_LONG, CommandLineLoader.OPTION_MODEL));
            helpFormatter.printHelp("psl", CommandLineLoader.getOptions(), true);
            return false;
        }

        if (!givenOptions.hasOption(CommandLineLoader.OPERATION_INFER) && (!givenOptions.hasOption(CommandLineLoader.OPERATION_LEARN))) {
            System.out.println(String.format("Missing required option: --%s/-%s.", CommandLineLoader.OPERATION_INFER_LONG, CommandLineLoader.OPERATION_INFER));
            helpFormatter.printHelp("psl", CommandLineLoader.getOptions(), true);
            return false;
        }

        return true;
    }

    public static void main(String[] args) {
        main(args, false);
    }

    public static void main(String[] args, boolean rethrow) {
        try {
            CommandLineLoader commandLineLoader = new CommandLineLoader(args);
            CommandLine givenOptions = commandLineLoader.getParsedOptions();
            // Return for command line parse errors or PSL errors.
            if ((givenOptions == null) || (!(isCommandLineValid(givenOptions)))) {
                return;
            }
            Launcher pslLauncher = new Launcher(givenOptions);
            pslLauncher.run();
        } catch (Exception ex) {
            if (rethrow) {
                throw new RuntimeException("Failed to run CLI: " + ex.getMessage(), ex);
            } else {
                System.err.println("Unexpected exception!");
                ex.printStackTrace(System.err);
                System.exit(1);
            }
        }
    }
}
