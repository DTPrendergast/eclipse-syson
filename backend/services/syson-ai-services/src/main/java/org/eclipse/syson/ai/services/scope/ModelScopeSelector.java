/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.syson.ai.services.scope;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.Relationship;
import org.eclipse.syson.sysml.metamodel.util.ElementUtil;
import org.springframework.stereotype.Service;

/**
 * Expands a user selection into the set of elements a generation will be shown.
 *
 * <p>
 * The expansion is deliberately explicit about how it walks the model. Containment is followed
 * through {@link Element#getOwnedElement()}, which already skips the intermediate membership
 * objects. References are followed through {@link Element#getOwnedRelationship()} and the related
 * elements each relationship names, rather than through {@code eCrossReferences()}: a typing such
 * as {@code part x : Def} is owned by {@code x} as a relationship whose other end is {@code Def},
 * so walking relationships reaches the definition, and it does so without depending on how EMF
 * treats derived references.
 * </p>
 *
 * <p>
 * Standard-library elements are never pulled in. They are large, they are identical in every
 * project, and a model that needs to know what {@code Base::Anything} is will not learn it usefully
 * from a fragment of the library.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class ModelScopeSelector {

    /**
     * Expands the given seeds according to the policy.
     *
     * @param seeds
     *            the elements the user selected; nulls and standard-library elements are dropped
     * @param policy
     *            the expansion and budget limits to respect
     * @return the resulting scope, which is empty when no seed survived filtering
     */
    public ModelScope select(List<Element> seeds, ModelScopePolicy policy) {
        Objects.requireNonNull(policy);
        var keptSeeds = new ArrayList<Element>();
        for (Element seed : Objects.requireNonNull(seeds)) {
            if (this.isEligible(seed) && !keptSeeds.contains(seed)) {
                keptSeeds.add(seed);
            }
        }

        var inScope = new LinkedHashSet<Element>(keptSeeds);
        boolean limitReached = this.trimToLimit(inScope, policy.maxElements());

        if (!limitReached) {
            limitReached = this.addOwnedClosure(inScope, keptSeeds, policy);
        }

        for (int hop = 0; hop < policy.relatedDepth() && !limitReached; hop++) {
            var reached = this.relatedElements(inScope);
            reached.removeAll(inScope);
            if (reached.isEmpty()) {
                break;
            }
            var added = new ArrayList<Element>();
            for (Element element : reached) {
                if (inScope.size() >= policy.maxElements()) {
                    limitReached = true;
                    break;
                }
                inScope.add(element);
                added.add(element);
            }
            if (!limitReached) {
                // A referenced definition without its features is barely context at all, so what a
                // relationship reaches is expanded the same way a seed is.
                limitReached = this.addOwnedClosure(inScope, added, policy);
            }
        }

        return new ModelScope(keptSeeds, List.copyOf(inScope), limitReached);
    }

    /**
     * Adds the owned elements of each of {@code roots} to {@code inScope}, down to
     * {@link ModelScopePolicy#ownedDepth()} levels.
     *
     * @return true if the element budget ran out during the walk
     */
    private boolean addOwnedClosure(LinkedHashSet<Element> inScope, List<Element> roots, ModelScopePolicy policy) {
        var frontier = new ArrayList<>(roots);
        boolean limitReached = false;
        for (int depth = 0; depth < policy.ownedDepth() && !frontier.isEmpty() && !limitReached; depth++) {
            var next = new ArrayList<Element>();
            for (Element element : frontier) {
                for (Element owned : element.getOwnedElement()) {
                    if (this.isEligible(owned) && !inScope.contains(owned) && !limitReached) {
                        if (inScope.size() >= policy.maxElements()) {
                            limitReached = true;
                        } else {
                            inScope.add(owned);
                            next.add(owned);
                        }
                    }
                }
            }
            frontier = next;
        }
        return limitReached;
    }

    /**
     * Every element named by a relationship owned by something already in scope.
     */
    private LinkedHashSet<Element> relatedElements(LinkedHashSet<Element> inScope) {
        var reached = new LinkedHashSet<Element>();
        for (Element element : inScope) {
            for (Relationship relationship : element.getOwnedRelationship()) {
                for (Element related : relationship.getRelatedElement()) {
                    if (this.isEligible(related)) {
                        reached.add(related);
                    }
                }
            }
        }
        return reached;
    }

    private boolean trimToLimit(LinkedHashSet<Element> inScope, int maxElements) {
        if (inScope.size() <= maxElements) {
            return false;
        }
        var kept = new ArrayList<>(inScope).subList(0, maxElements);
        inScope.retainAll(new LinkedHashSet<>(kept));
        return true;
    }

    private boolean isEligible(Element element) {
        return element != null && !ElementUtil.isFromStandardLibrary(element);
    }
}
