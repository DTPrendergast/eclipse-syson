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
package org.eclipse.syson.table.verification.view;

import java.util.List;

import org.eclipse.sirius.components.collaborative.tables.api.ITableInput;
import org.eclipse.sirius.components.collaborative.tables.api.IToolMenuEntriesProvider;
import org.eclipse.sirius.components.collaborative.tables.dto.ToolMenuEntry;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.tables.Table;
import org.eclipse.sirius.components.tables.descriptions.TableDescription;
import org.springframework.stereotype.Service;

/**
 * Provide tools to Verification Table Views.
 *
 * @author DTPrendergast
 */
@Service
public class VTVTableToolMenuEntriesProvider implements IToolMenuEntriesProvider {

    public static final String ADD_VERIFICATION_CASE_TABLE_TOOL_ENTRY = "add-verification-case-table-tool-entry";

    @Override
    public boolean canHandle(IEditingContext editingContext, Table table, TableDescription tableDescription, ITableInput tableInput) {
        return tableDescription.getLabel().equals(VTVTableDescriptionProvider.DESCRIPTION_NAME);
    }

    @Override
    public List<ToolMenuEntry> getToolMenuEntries(IEditingContext editingContext, Table table, TableDescription tableDescription, ITableInput tableInput) {
        return List.of(new ToolMenuEntry(ADD_VERIFICATION_CASE_TABLE_TOOL_ENTRY, "Create verification case", List.of("/images/createRequirement.svg")));
    }
}
