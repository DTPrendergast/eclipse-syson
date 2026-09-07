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

import java.util.List;

import org.eclipse.sirius.components.core.api.ILabelServiceDelegate;
import org.eclipse.sirius.components.core.api.labels.StyledString;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.OwningMembership;
import org.springframework.stereotype.Service;

/**
 * Distinguishes elements carrying the {@code OperationalConcept} metadata (see
 * {@code syson-operational-concept-library}'s README.md) with a dedicated icon in the explorer, per item 2's
 * acceptance criterion ("see it distinguished in the explorer"). Declines (returns {@code false} from
 * {@link #canHandle(Object)}) everything else, letting {@code SysMLv2LabelService} and other delegates handle it
 * as usual -- same "chain of responsibility" pattern as {@code SysONSelectionDialogFragmentLabelService}.
 *
 * @author DTPrendergast
 */
@Service
public class OperationalConceptLabelServiceDelegate implements ILabelServiceDelegate {

    /**
     * Reused for both the explorer tree item and any other place this delegate's label is shown (e.g. reference
     * widgets): there is no dedicated "operational concept" icon asset, so this reuses the closest existing one --
     * an operational concept is, in this project's model pattern, primarily a package of use cases.
     */
    private static final String ICON_PATH = "/icons/full/obj16/UseCaseDefinition.svg";

    @Override
    public boolean canHandle(Object object) {
        return object instanceof Element element && this.hasOperationalConceptMetadata(element);
    }

    @Override
    public StyledString getStyledLabel(Object object) {
        String label = "";
        if (object instanceof Element element && element.getName() != null) {
            label = element.getName();
        }
        return StyledString.of(label);
    }

    @Override
    public List<String> getImagePaths(Object object) {
        return List.of(ICON_PATH);
    }

    private boolean hasOperationalConceptMetadata(Element element) {
        return element.getOwnedRelationship().stream()
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                .filter(MetadataUsage.class::isInstance)
                .map(MetadataUsage.class::cast)
                .anyMatch(metadataUsage -> metadataUsage.getMetadataDefinition() != null
                        && "OperationalConcept".equals(metadataUsage.getMetadataDefinition().getName()));
    }
}
