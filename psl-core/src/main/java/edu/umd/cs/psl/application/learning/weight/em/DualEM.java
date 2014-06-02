/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.application.learning.weight.em;

import java.text.DecimalFormat;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.learning.weight.maxlikelihood.VotedPerceptron;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.optimizer.lbfgs.ConvexFunc;
import edu.umd.cs.psl.reasoner.admm.ADMMReasoner;

/**
 * EM algorithm which fits a point distribution to the single most probable
 * assignment of truth values to the latent variables during the E-step. 
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class DualEM extends ExpectationMaximization implements ConvexFunc {

	private static final Logger log = LoggerFactory.getLogger(DualEM.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "dualem";
	
	/**
	 * Key for Boolean property that indicates whether to use AdaGrad subgradient
	 * scaling, the adaptive subgradient algorithm of
	 * John Duchi, Elad Hazan, Yoram Singer (JMLR 2010).
	 * 
	 * If TRUE, will override other step scheduling and scaling options.
	 */
	public static final String ADAGRAD_KEY = CONFIG_PREFIX + ".adagrad";
	/** Default value for ADAGRAD_KEY */
	public static final boolean ADAGRAD_DEFAULT = false;

	double[] scalingFactor;
	double[] dualObservedIncompatibility, dualExpectedIncompatibility;
	private final boolean useAdaGrad;

	public DualEM(Model model, Database rvDB, Database observedDB,
			ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		scalingFactor = new double[kernels.size()];
		useAdaGrad = config.getBoolean(ADAGRAD_KEY, ADAGRAD_DEFAULT);
	}

	/**
	 * Minimizes the KL divergence by setting the latent variables to their
	 * most probable state conditioned on the evidence and the labeled
	 * random variables.
	 * <p>
	 * This method assumes that the inferred truth values will be used
	 * immediately by {@link VotedPerceptron#computeObservedIncomp()}.
	 */
	@Override
	protected void minimizeKLDivergence() {
		inferLatentVariables();
	}

	@Override
	protected double[] computeExpectedIncomp() {
		dualExpectedIncompatibility = new double[kernels.size() + immutableKernels.size()];

		/* Computes the MPE state */
		reasoner.optimize();

		ADMMReasoner admm = (ADMMReasoner) reasoner;

		// Compute the dual incompatbility for each ADMM subproblem
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				dualExpectedIncompatibility[i] += admm.getDualIncompatibility(gk);
			}
		}

		for (int i = 0; i < immutableKernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(immutableKernels.get(i))) {
				dualExpectedIncompatibility[kernels.size() + i] += admm.getDualIncompatibility(gk);
			}
		}

		return Arrays.copyOf(dualExpectedIncompatibility, kernels.size());
	}

	@Override
	protected double[] computeObservedIncomp() {
		numGroundings = new double[kernels.size()];
		dualObservedIncompatibility = new double[kernels.size() + immutableKernels.size()];
		setLabeledRandomVariables();

		ADMMReasoner admm = (ADMMReasoner) latentVariableReasoner;
		
		/* Computes the observed incompatibilities and numbers of groundings */
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : latentVariableReasoner.getGroundKernels(kernels.get(i))) {
				dualObservedIncompatibility[i] += admm.getDualIncompatibility(gk);
				numGroundings[i]++;
			}
		}
		for (int i = 0; i < immutableKernels.size(); i++) {
			for (GroundKernel gk : latentVariableReasoner.getGroundKernels(immutableKernels.get(i))) {
				dualObservedIncompatibility[kernels.size() + i] += admm.getDualIncompatibility(gk);
			}
		}

		return Arrays.copyOf(dualObservedIncompatibility, kernels.size());
	}

	@Override
	protected double computeLoss() {
		double loss = 0.0;
		for (int i = 0; i < kernels.size(); i++)
			loss += kernels.get(i).getWeight().getWeight() * (dualObservedIncompatibility[i] - dualExpectedIncompatibility[i]);
		for (int i = 0; i < immutableKernels.size(); i++)
			loss += immutableKernels.get(i).getWeight().getWeight() * (dualObservedIncompatibility[kernels.size() + i] - dualExpectedIncompatibility[kernels.size() + i]);
		return loss;
	}

	private void subgrad() {
		log.info("Starting adagrad");
		double [] weights = new double[kernels.size()];
		for (int i = 0; i < kernels.size(); i++)
			weights[i] = kernels.get(i).getWeight().getWeight();

		double [] avgWeights = new double[kernels.size()];

		double [] gradient = new double[kernels.size()];
		double [] scale = new double[kernels.size()];
		double objective = 0;
		for (int step = 0; step < iterations; step++) {
			objective = getValueAndGradient(gradient, weights);
			double gradNorm = 0;
			double change = 0;
			for (int i = 0; i < kernels.size(); i++) {
				if (useAdaGrad)
					scale[i] += gradient[i] * gradient[i];
				else if (scheduleStepSize)
					scale[i] = Math.pow((double) (step + 1), 2);
				else
					scale[i] = 1.0;
				
				gradNorm += Math.pow(weights[i] - Math.max(0, weights[i] - gradient[i]), 2);
				
				if (scale[i] > 0.0) {
					double coeff = stepSize / scale[i];
					weights[i] = Math.max(0, weights[i] - coeff * gradient[i]);
					change += Math.pow(weights[i] - kernels.get(i).getWeight().getWeight(), 2);
				}
				avgWeights[i] = (1 - (1.0 / (double) (step + 1.0))) * avgWeights[i] + (1.0 / (double) (step + 1.0)) * weights[i];
			}

			gradNorm = Math.sqrt(gradNorm);
			change = Math.sqrt(change);
			DecimalFormat df = new DecimalFormat("0.0000E00");
			if (step % 1 == 0)
				log.info("Iter {}, obj: {}, norm grad: " + df.format(gradNorm) + ", change: " + df.format(change), step, df.format(objective));

			if (change < tolerance) {
				log.info("Change in w ({}) is less than tolerance. Finishing adagrad.", change);
				break;
			}
		}

		log.info("Adagrad learning finished with final objective value {}", objective);

		for (int i = 0; i < kernels.size(); i++) {
			kernels.get(i).setWeight(new PositiveWeight(weights[i]));
		}
	}

	@Override
	protected void doLearn() {
		int maxIter = ((ADMMReasoner) reasoner).getMaxIter();
		int admmIterations = 10;
		((ADMMReasoner) reasoner).setMaxIter(admmIterations);
		((ADMMReasoner) latentVariableReasoner).setMaxIter(admmIterations);
		if (augmentLoss)
			addLossAugmentedKernels();
		subgrad();
		if (augmentLoss)
			removeLossAugmentedKernels();

		((ADMMReasoner) reasoner).setMaxIter(maxIter);
		((ADMMReasoner) latentVariableReasoner).setMaxIter(maxIter);

	}

	@Override
	public double getValueAndGradient(double[] gradient, double[] weights) {
		for (int i = 0; i < kernels.size(); i++) {
			kernels.get(i).setWeight(new PositiveWeight(weights[i]));
		}
		minimizeKLDivergence();
		computeObservedIncomp();

		reasoner.changedGroundKernelWeights();
		computeExpectedIncomp();

		double loss = 0.0;
		for (int i = 0; i < kernels.size(); i++)
			loss += weights[i] * (dualObservedIncompatibility[i] - dualExpectedIncompatibility[i]);
		for (int i = 0; i < immutableKernels.size(); i++)
			loss += immutableKernels.get(i).getWeight().getWeight() * (dualObservedIncompatibility[kernels.size() + i] - dualExpectedIncompatibility[kernels.size() + i]);
		double eStepLagrangianPenalty = ((ADMMReasoner) latentVariableReasoner).getLagrangianPenalty();
		double eStepAugLagrangianPenalty = ((ADMMReasoner) latentVariableReasoner).getAugmentedLagrangianPenalty();
		double mStepLagrangianPenalty = ((ADMMReasoner) reasoner).getLagrangianPenalty();
		double mStepAugLagrangianPenalty = ((ADMMReasoner) reasoner).getAugmentedLagrangianPenalty();
		loss += eStepLagrangianPenalty + eStepAugLagrangianPenalty - mStepLagrangianPenalty - mStepAugLagrangianPenalty;
		
		log.info("E Penalty: {}, E Aug Penalty: {}, M Penalty: {}, M Aug Penalty: {}",
				new Double[] {eStepLagrangianPenalty, eStepAugLagrangianPenalty, mStepLagrangianPenalty, mStepAugLagrangianPenalty});

		
		double regularizer = computeRegularizer();

		if (null != gradient) 
			for (int i = 0; i < kernels.size(); i++) {
				gradient[i] = dualObservedIncompatibility[i] - dualExpectedIncompatibility[i];
				if (!useAdaGrad && scaleGradient)
					gradient[i] /= numGroundings[i];
				gradient[i] += l2Regularization * weights[i] + l1Regularization;
			}

		return loss + regularizer;
	}

}
