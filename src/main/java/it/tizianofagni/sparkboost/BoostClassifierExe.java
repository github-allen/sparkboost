/*
 *
 * ****************
 * This file is part of nlp4sparkml software package (https://github.com/tizfa/nlp4sparkml).
 *
 * Copyright 2016 Tiziano Fagni (tiziano.fagni@isti.cnr.it)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ******************
 */

package it.tizianofagni.sparkboost;

import org.apache.commons.cli.*;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import java.util.Arrays;

/**
 * @author Tiziano Fagni (tiziano.fagni@isti.cnr.it)
 */
public class BoostClassifierExe {
    public static void main(String[] args) {

        Options options = new Options();
        options.addOption("b", "binaryProblem", false, "Indicate if the input dataset contains a binary problem and not a multilabel one");
        options.addOption("z", "labels0based", false, "Indicate if the labels IDs in the dataset to classifyLibSvmWithResults are already assigned in the range [0, numLabels-1] included");
        options.addOption("l", "enableSparkLogging", false, "Enable logging messages of Spark");
        options.addOption("w", "windowsLocalModeFix", true, "Set the directory containing the winutils.exe command");
        options.addOption("p", "parallelismDegree", true, "Set the parallelism degree (default: number of available cores in the Spark runtime");
        options.addOption("sdc", "singleDocumentClassification", false, "Process results one document at a time (useful on big test set to limit the usage of RAM memory)");

        CommandLineParser parser = new BasicParser();
        CommandLine cmd = null;
        String[] remainingArgs = null;
        try {
            cmd = parser.parse(options, args);
            remainingArgs = cmd.getArgs();
            if (remainingArgs.length != 3)
                throw new ParseException("You need to specify all mandatory parameters");
        } catch (ParseException e) {
            System.out.println("Parsing failed.  Reason: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(BoostClassifierExe.class.getSimpleName() + " [OPTIONS] <inputFile> <inputModel> <outputFile>", options);
            System.exit(-1);
        }

        boolean binaryProblem = false;
        if (cmd.hasOption("b"))
            binaryProblem = true;
        boolean labels0Based = false;
        if (cmd.hasOption("z"))
            labels0Based = true;
        boolean enablingSparkLogging = false;
        if (cmd.hasOption("l"))
            enablingSparkLogging = true;

        if (cmd.hasOption("w")) {
            System.setProperty("hadoop.home.dir", cmd.getOptionValue("w"));
        }

        String inputFile = remainingArgs[0];
        String inputModel = remainingArgs[1];
        String outputFile = remainingArgs[2];


        long startTime = System.currentTimeMillis();

        // Disable Spark logging.
        if (!enablingSparkLogging) {
            Logger.getLogger("org").setLevel(Level.OFF);
            Logger.getLogger("akka").setLevel(Level.OFF);
        }

        // Create and configure Spark context.
        SparkConf conf = new SparkConf().setAppName("Spark MPBoost classifier");
        JavaSparkContext sc = new JavaSparkContext(conf);

        // Load boosting classifier from disk.
        BoostClassifier classifier = DataUtils.loadModel(sc, inputModel);

        // Get the parallelism degree.
        int parallelismDegree = sc.defaultParallelism();
        if (cmd.hasOption("p")) {
            parallelismDegree = Integer.parseInt(cmd.getOptionValue("p"));
        }

        if (!cmd.hasOption("sdc")) {

            // Classify documents contained in "inputFile", a file in libsvm format.
            ClassificationResults results = classifier.classifyLibSvmWithResults(sc, inputFile, parallelismDegree, labels0Based, binaryProblem);

            // Write classification results to disk.
            StringBuilder sb = new StringBuilder();
            sb.append("**** Effectiveness\n");
            sb.append(results.getCt().toString() + "\n");
            sb.append("********\n");
            for (int i = 0; i < results.getNumDocs(); i++) {
                int docID = results.getDocuments()[i];
                int[] labels = results.getLabels()[i];
                int[] goldLabels = results.getGoldLabels()[i];
                sb.append("DocID: " + docID + ", Labels assigned: " + Arrays.toString(labels) + ", Labels scores: " + Arrays.toString(results.getScores()[i]) + ", Gold labels: " + Arrays.toString(goldLabels) + "\n");
            }
            try {
                DataUtils.saveHadoopTextFile(outputFile, sb.toString());
            } catch (Exception e) {
                throw new RuntimeException("Writing classisfication results", e);
            }
        } else {
            classifier.classifyLibSvm(sc, inputFile, parallelismDegree, labels0Based, binaryProblem, 100000, outputFile);
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Execution time: " + (endTime - startTime) + " milliseconds.");
    }
}
