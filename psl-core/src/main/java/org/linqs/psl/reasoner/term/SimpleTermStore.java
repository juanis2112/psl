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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A straightforward term store.
 */
public abstract class SimpleTermStore<T extends ReasonerTerm> extends TermStore<T> {
    protected ArrayList<T> allTerms;

    /**
     * A map from component ID to the terms in that component.
     * The component ID is the index of the root atom in the component.
     */
    protected Map<Integer, List<T>> connectedComponents;

    public SimpleTermStore(Database database, TermGenerator<T> termGenerator) {
        super(database, termGenerator);
        allTerms = new ArrayList<T>();
        connectedComponents = new HashMap<Integer, List<T>>();
    }

    @Override
    protected synchronized int add(ReasonerTerm term) {
        T newTerm = (T)term;
        allTerms.add(newTerm);

        // Add to the connected component map.
        int termRootIndex = atomStore.findAtomRoot(atomStore.getAtom(newTerm.atomIndexes[0]));
        GroundAtom rootAtom = atomStore.getAtom(termRootIndex);

        if (connectedComponents.containsKey(termRootIndex)) {
            connectedComponents.get(termRootIndex).add(newTerm);
        } else {
            ArrayList<T> component = new ArrayList<T>();
            component.add(newTerm);
            connectedComponents.put(termRootIndex, component);
        }

        // Unify the components of the atoms in this term.
        // TODO(Charles): Potential optimization: do not allow union with fixed atoms.
        for (int i = 1; i < newTerm.size; i++) {
            int nextAtomRootIndex = atomStore.findAtomRoot(atomStore.getAtom(newTerm.atomIndexes[i]));
            GroundAtom nextRootAtom = atomStore.getAtom(nextAtomRootIndex);

            if (nextAtomRootIndex == termRootIndex) {
                // Already in the same component.
                continue;
            }

            atomStore.union(rootAtom, nextRootAtom);

            // Merge the components.
            if (!connectedComponents.containsKey(nextAtomRootIndex)) {
                // No component for the next atom.
                continue;
            }

            connectedComponents.get(termRootIndex).addAll(connectedComponents.get(nextAtomRootIndex));
            connectedComponents.remove(nextAtomRootIndex);
        }

        return 1;
    }

    /**
     * Remove any existing terms and prepare for a new set.
     */
    @Override
    public void clear() {
        allTerms.clear();
        connectedComponents.clear();
    }

    @Override
    public void close() {
        super.close();
        allTerms = null;
        connectedComponents = null;
    }

    @Override
    public void ensureCapacity(long capacity) {
        allTerms.ensureCapacity((int)capacity);
    }

    @Override
    public T get(long index) {
        assert(index <= Integer.MAX_VALUE);

        return allTerms.get((int)index);
    }

    public List<T> getAllTerms() {
    	return allTerms;
    }

    public Map<Integer, List<T>> getConnectedComponents() {
        return connectedComponents;
    }

    public List<Integer> getConnectedComponentKeys() {
    	return new ArrayList<Integer>(connectedComponents.keySet());
    }

    @Override
    public Iterator<T> iterator() {
        return allTerms.iterator();
    }

    @Override
    public long size() {
        return (long) allTerms.size();
    }
}
