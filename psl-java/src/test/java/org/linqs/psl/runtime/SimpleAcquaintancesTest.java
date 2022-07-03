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
package org.linqs.psl.runtime;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.linqs.psl.database.ReadableDatabase;
import org.linqs.psl.config.RuntimeOptions;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.evaluation.statistics.DiscreteEvaluator;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueStringID;

import org.junit.Test;

import java.nio.file.Paths;

public class SimpleAcquaintancesTest extends RuntimeTest {
    @Test
    public void testBase() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "base.data").toString();

        runInference(modelPath, dataPath);
    }

    @Test
    public void testEval() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "base.data").toString();

        RuntimeOptions.INFERENCE_EVAL.set(ContinuousEvaluator.class.getName());

        runInference(modelPath, dataPath);
    }

    @Test
    public void testMultipleEval() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "base.data").toString();

        String evals = ContinuousEvaluator.class.getName() + "," + DiscreteEvaluator.class.getName();
        RuntimeOptions.INFERENCE_EVAL.set(evals);

        runInference(modelPath, dataPath);
    }

    @Test
    public void testTypes() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "base_types.data").toString();

        runInference(modelPath, dataPath);
    }

    @Test
    public void testMixedTypes() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances-mixed", "base.data").toString();

        runInference(modelPath, dataPath);
    }

    @Test
    public void testBlock() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "base_block.data").toString();

        runInference(modelPath, dataPath);
    }

    @Test
    public void testErrorUndeclaredPredicate() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "error_undeclared_predicate.data").toString();

        try {
            runInference(modelPath, dataPath);
            fail("Error not thrown on non-existent predicate.");
        } catch (RuntimeException ex) {
            // Expected.
        }
    }

    @Test
    public void testErrorDataInFunctional() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "error_data_in_functional.data").toString();

        try {
            runInference(modelPath, dataPath);
            fail("Error not thrown on data in a functional predicate.");
        } catch (RuntimeException ex) {
            // Expected.
        }
    }

    @Test
    public void testFunctional() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances-functional.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "base_functional.data").toString();

        runInference(modelPath, dataPath);
    }

    @Test
    public void testErrorBadTopLevelKey() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "error_bad_top_key.data").toString();

        try {
            runInference(modelPath, dataPath);
            fail("Error not thrown on bad top level key.");
        } catch (RuntimeException ex) {
            // Expected.
        }
    }

    @Test
    public void testErrorObservedTargets() {
        String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
        String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "error_obs_targets.data").toString();

        try {
            runInference(modelPath, dataPath);
            fail("Error not thrown on atoms that are observed and targets.");
        } catch (RuntimeException ex) {
            // Expected.
        }
    }

    // Not an actual similarity.
    public static class SimNameExternalFunction implements ExternalFunction {
        @Override
        public double getValue(ReadableDatabase db, Constant... args) {
            String a = ((UniqueStringID)args[0]).getID();
            String b = ((UniqueStringID)args[1]).getID();

            return Math.abs(a.length() - b.length()) / (double)(Math.max(a.length(), b.length()));
        }

        @Override
        public int getArity() {
            return 2;
        }

        @Override
        public ConstantType[] getArgumentTypes() {
            return new ConstantType[] {ConstantType.UniqueStringID, ConstantType.UniqueStringID};
        }
    }
}
