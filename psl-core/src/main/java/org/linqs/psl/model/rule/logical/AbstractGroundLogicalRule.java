/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
package org.linqs.psl.model.rule.logical;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.util.HashCode;
import org.linqs.psl.util.IteratorUtils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for all ground logical rules.
 */
public abstract class AbstractGroundLogicalRule implements GroundRule {
    protected final AbstractLogicalRule rule;
    protected final List<GroundAtom> posLiterals;
    protected final List<GroundAtom> negLiterals;
    protected final GeneralFunction dissatisfaction;

    private final int hashcode;

    /**
     * @param posLiterals the positive literals (ground atoms) in the negated DNF.
     * @param negLiterals the negative literals (ground atoms) in the negated DNF.
     */
    protected AbstractGroundLogicalRule(AbstractLogicalRule rule, List<GroundAtom> posLiterals, List<GroundAtom> negLiterals) {
        this.rule = rule;
        this.posLiterals = Collections.unmodifiableList(new ArrayList<GroundAtom>(posLiterals));
        this.negLiterals = Collections.unmodifiableList(new ArrayList<GroundAtom>(negLiterals));

        // Construct function definition.
        dissatisfaction = getFunction(true);

        // Construct the hash code.
        int hash = HashCode.build(rule);

        for (int i = 0; i < this.posLiterals.size(); i++) {
            hash = HashCode.build(hash, this.posLiterals.get(i));
        }

        for (int i = 0; i < this.negLiterals.size(); i++) {
            hash = HashCode.build(hash, this.negLiterals.get(i));
        }

        hashcode = hash;
    }

    /**
     * Construct function definition representing the ground rule's dissatisfaction.
     * The function returned from this will never be squared.
     * Child classes that have squaring information are responsible for setting that.
     */
    protected GeneralFunction getFunction(boolean mergeConstants) {
        // nonNegative refers to having a hinge at 0 (i.e. max(0.0, X)).
        // If there are at least two literals, then there will be a hinge
        // (otherwise it will just be linear).
        boolean nonNegative = (posLiterals.size() + negLiterals.size() > 1);
        GeneralFunction function = new GeneralFunction(nonNegative, false, posLiterals.size() + negLiterals.size(), mergeConstants);

        // Note that the pos/neg qualifier are w.r.t the negated DNF.
        // This means that the potential function being constructed here is actually the
        // ground rule's dissatisfaction.

        for (int i = 0; i < posLiterals.size(); i++) {
            function.add(1.0f, posLiterals.get(i));
        }

        for (int i = 0; i < this.negLiterals.size(); i++) {
            function.add(-1.0f, this.negLiterals.get(i));
        }

        // Adding a constant 1.0 overall and subtracting 1.0 for each positive term (in the negated DNF)
        // will make this potential function the same as the dissatisfaction of the original (non-negated) ground rule.
        function.add(1.0f - this.posLiterals.size());

        return function;
    }

    @Override
    public Set<GroundAtom> getAtoms() {
        HashSet<GroundAtom> atoms = new HashSet<GroundAtom>();

        for (GroundAtom atom : posLiterals) {
            atoms.add(atom);
        }

        for (GroundAtom atom : negLiterals) {
            atoms.add(atom);
        }

        return atoms;
    }

    public List<GroundAtom> getPositiveAtoms() {
        return posLiterals;
    }

    public List<GroundAtom> getNegativeAtoms() {
        return negLiterals;
    }

    public int size() {
        return posLiterals.size() + negLiterals.size();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (other == null
                || !(other instanceof AbstractGroundLogicalRule)
                || this.hashCode() != other.hashCode()) {
            return false;
        }

        AbstractGroundLogicalRule otherRule = (AbstractGroundLogicalRule)other;
        if (!rule.equals(otherRule.getRule())) {
            return false;
        }

        return posLiterals.equals(otherRule.posLiterals)
                && negLiterals.equals(otherRule.negLiterals);
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    @Override
    public String baseToString() {
        // Negate the clause again to show the positive clause.
        Formula[] literals = new Formula[posLiterals.size() + negLiterals.size()];
        int i;

        for (i = 0; i < posLiterals.size(); i++) {
            literals[i] = new Negation(posLiterals.get(i));
        }

        for (int j = 0; j < negLiterals.size(); j++) {
            literals[i++] = negLiterals.get(j);
        }

        return (literals.length > 1) ? new Disjunction(literals).toString() : literals[0].toString();
    }

    @Override
    public String toString() {
        return baseToString();
    }
}
