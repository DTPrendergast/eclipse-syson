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
package org.eclipse.syson.operational.concept.library;

import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.Package;
import org.eclipse.syson.sysml.SysmlFactory;
import org.springframework.stereotype.Service;

/**
 * The package an operational concept's use cases live in.
 *
 * <p>
 * Generated use cases are collected in a package of their own beneath the operational concept,
 * rather than scattered among the concept's other content. That is a modelling convention rather
 * than anything the standard requires, so it is expressed once here and not restated by each
 * caller.
 * </p>
 *
 * <p>
 * The narrative stays one level above, on the concept itself. That matters for generation: a task
 * seeded on this package reaches the narrative by walking one level up, which is what the use case
 * assistant's scope policy does.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class UseCasePackageService {

    /** Declared with a space, because it is a label a person reads in the explorer. */
    public static final String USE_CASES_PACKAGE_NAME = "Use Cases";

    /**
     * The operational concept's use case package, creating it if it is not there yet.
     *
     * @param operationalConcept
     *            the package holding the concept
     * @return the use case package, or null when the argument is not a package
     */
    public Package findOrCreate(Element operationalConcept) {
        Package found = this.find(operationalConcept);
        if (found == null && operationalConcept instanceof Package concept) {
            found = SysmlFactory.eINSTANCE.createPackage();
            found.setDeclaredName(USE_CASES_PACKAGE_NAME);
            OwningMembership owningMembership = SysmlFactory.eINSTANCE.createOwningMembership();
            concept.getOwnedRelationship().add(owningMembership);
            owningMembership.getOwnedRelatedElement().add(found);
        }
        return found;
    }

    /**
     * The operational concept's use case package, or null when there is none.
     *
     * @param operationalConcept
     *            the package holding the concept
     * @return the use case package, or null
     */
    public Package find(Element operationalConcept) {
        Package found = null;
        if (operationalConcept != null) {
            found = operationalConcept.getOwnedElement().stream()
                    .filter(Package.class::isInstance)
                    .map(Package.class::cast)
                    .filter(candidate -> USE_CASES_PACKAGE_NAME.equals(candidate.getDeclaredName()))
                    .findFirst()
                    .orElse(null);
        }
        return found;
    }
}
