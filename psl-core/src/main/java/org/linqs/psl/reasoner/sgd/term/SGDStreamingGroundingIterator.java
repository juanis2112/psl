/**
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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.streaming.StreamingGroundingIterator;
import org.linqs.psl.util.RuntimeStats;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class SGDStreamingGroundingIterator extends StreamingGroundingIterator<SGDObjectiveTerm> {
    public SGDStreamingGroundingIterator(
            SGDStreamingTermStore parentStore, List<Rule> rules,
            List<SGDObjectiveTerm> termCache, List<SGDObjectiveTerm> termPool,
            ByteBuffer termBuffer, int pageSize, int numPages) {
        super(parentStore, rules, termCache, termPool, termBuffer, pageSize, numPages);
    }
}
