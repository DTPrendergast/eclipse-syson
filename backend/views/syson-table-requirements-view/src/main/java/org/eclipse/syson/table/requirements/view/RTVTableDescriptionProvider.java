/*******************************************************************************
 * Copyright (c) 2025, 2026 Obeo.
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
package org.eclipse.syson.table.requirements.view;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.tables.descriptions.TableDescription;
import org.eclipse.sirius.components.view.RepresentationDescription;
import org.eclipse.sirius.components.view.builder.generated.table.TableBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IRepresentationDescriptionProvider;
import org.eclipse.sirius.components.view.table.CellDescription;
import org.eclipse.sirius.components.view.table.ColumnDescription;
import org.eclipse.sirius.components.view.table.RowContextMenuEntry;
import org.eclipse.syson.services.DeleteService;
import org.eclipse.syson.sysml.SysmlPackage;
import org.eclipse.syson.table.requirements.view.services.RTVMutationServices;
import org.eclipse.syson.table.requirements.view.services.RTVQueryServices;
import org.eclipse.syson.util.AQLConstants;
import org.eclipse.syson.util.ServiceMethod;
import org.eclipse.syson.util.SysMLMetamodelHelper;

/**
 * Description of the Non-Standard Requirements Table Views using the ViewBuilder API from Sirius Web.
 *
 * @author arichard
 */
public class RTVTableDescriptionProvider implements IRepresentationDescriptionProvider {

    public static final String DESCRIPTION_NAME = "Requirements Table View";

    private static final String NEW_VALUE = "newValue";

    private static final String NEW_VALUE_EXPRESSION = "aql:newValue";

    private final TableBuilders tableBuilders = new TableBuilders();

    private final ViewBuilders viewBuilders = new ViewBuilders();

    @Override
    public RepresentationDescription create(IColorProvider colorProvider) {
        String domainType = SysMLMetamodelHelper.buildQualifiedName(SysmlPackage.eINSTANCE.getNamespace());

        var reqRowDescription = this.tableBuilders.newRowDescription()
                .name("RTV-RowDescription-Requirement")
                .semanticCandidatesExpression(
                        "aql:self.getExposedRequirements(expandedIds, expandAll)->sortAndFilterRequirements(columnSort, columnFilters, globalFilterData)->toPaginatedData(cursor,direction,size)")
                .contextMenuEntries(this.createContextMenuEntries().toArray(RowContextMenuEntry[]::new))
                .depthLevelExpression("0")
                .headerIconExpression("/icons/full/obj16/RequirementUsage.svg")
                .headerLabelExpression("")
                .initialHeightExpression("-1")
                .isResizableExpression(AQLConstants.AQL_FALSE)
                .depthLevelExpression(ServiceMethod.of0(RTVQueryServices::getRequirementDepthLevel).aqlSelf())
                .hasChildrenExpression(ServiceMethod.of0(RTVQueryServices::hasRequirementChildren).aqlSelf())
                .build();

        return this.tableBuilders.newTableDescription()
                .name(DESCRIPTION_NAME)
                .titleExpression("aql:'view'+ Sequence{self.existingViewUsagesCountForRepresentationCreation(), 1}->sum()")
                .domainType(domainType)
                .columnDescriptions(this.createColumns().toArray(ColumnDescription[]::new))
                .cellDescriptions(this.createCells().toArray(CellDescription[]::new))
                .rowDescription(reqRowDescription)
                .pageSizeOptionsExpression("aql:Sequence{10,20,50}")
                .useStripedRowsExpression("aql:true")
                .enableSubRows(true)
                .build();
    }

    private List<ColumnDescription> createColumns() {
        List<ColumnDescription> columns = new ArrayList<>();

        var declaredNameColumnDescription = this.tableBuilders.newColumnDescription()
                .name("RTV-ColumnDescription-DeclaredName")
                .semanticCandidatesExpression("aql:'DeclaredName'")
                .headerLabelExpression("DeclaredName")
                .initialWidthExpression("250")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build();

        var reqIdColumnDescription = this.tableBuilders.newColumnDescription()
                .name("RTV-ColumnDescription-ReqId")
                .semanticCandidatesExpression("aql:'ReqId'")
                .headerLabelExpression("ReqId")
                .initialWidthExpression("150")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build();

        var docColumnDescription = this.tableBuilders.newColumnDescription()
                .name("RTV-ColumnDescription-Documentation")
                .semanticCandidatesExpression("aql:'Documentation'")
                .headerLabelExpression("Documentation")
                .initialWidthExpression("400")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build();

        var verificationProcedureIdColumnDescription = this.tableBuilders.newColumnDescription()
                .name("RTV-ColumnDescription-VerificationProcedureId")
                .semanticCandidatesExpression("aql:'VerificationProcedureId'")
                .headerLabelExpression("Verification Procedure ID")
                .initialWidthExpression("200")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build();

        var parentRequirementsColumnDescription = this.tableBuilders.newColumnDescription()
                .name("RTV-ColumnDescription-ParentRequirements")
                .semanticCandidatesExpression("aql:'ParentRequirements'")
                .headerLabelExpression("Parent Requirements")
                .initialWidthExpression("200")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build();

        var requirementTypeColumnDescription = this.tableBuilders.newColumnDescription()
                .name("RTV-ColumnDescription-RequirementType")
                .semanticCandidatesExpression("aql:'RequirementType'")
                .headerLabelExpression("Requirement Type")
                .initialWidthExpression("150")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build();

        var thresholdOrObjectiveColumnDescription = this.tableBuilders.newColumnDescription()
                .name("RTV-ColumnDescription-ThresholdOrObjective")
                .semanticCandidatesExpression("aql:'ThresholdOrObjective'")
                .headerLabelExpression("Threshold or Objective")
                .initialWidthExpression("150")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build();

        var categoryColumnDescription = this.tableBuilders.newColumnDescription()
                .name("RTV-ColumnDescription-Category")
                .semanticCandidatesExpression("aql:'Category'")
                .headerLabelExpression("Category")
                .initialWidthExpression("150")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build();

        columns.add(declaredNameColumnDescription);
        columns.add(reqIdColumnDescription);
        columns.add(docColumnDescription);
        columns.add(verificationProcedureIdColumnDescription);
        columns.add(parentRequirementsColumnDescription);
        columns.add(requirementTypeColumnDescription);
        columns.add(thresholdOrObjectiveColumnDescription);
        columns.add(categoryColumnDescription);

        return columns;
    }

    private List<CellDescription> createCells() {
        List<CellDescription> cells = new ArrayList<>();
        cells.addAll(this.createBaseCells());
        cells.addAll(this.createRequirementAttributeCells());
        return cells;
    }

    private List<CellDescription> createBaseCells() {
        List<CellDescription> cells = new ArrayList<>();

        var declaredNameCellDescription = this.tableBuilders.newCellDescription()
                .name("RTV-CellDescription-DeclaredName")
                .preconditionExpression("aql:columnTargetObject == 'DeclaredName'")
                .valueExpression("aql:self.declaredName")
                .cellWidgetDescription(this.tableBuilders.newCellTextfieldWidgetDescription()
                        .body(this.viewBuilders.newSetValue()
                                .featureName("declaredName")
                                .valueExpression(NEW_VALUE_EXPRESSION)
                                .build())
                        .build())
                .build();

        var reqIdCellDescription = this.tableBuilders.newCellDescription()
                .name("RTV-CellDescription-ReqId")
                .preconditionExpression("aql:columnTargetObject == 'ReqId'")
                .valueExpression("aql:self.reqId")
                .cellWidgetDescription(this.tableBuilders.newCellTextfieldWidgetDescription()
                        .body(this.viewBuilders.newSetValue()
                                .featureName("reqId")
                                .valueExpression(NEW_VALUE_EXPRESSION)
                                .build())
                        .build())
                .build();

        var docCellDescription = this.tableBuilders.newCellDescription()
                .name("RTV-CellDescription-Documentation")
                .preconditionExpression("aql:columnTargetObject == 'Documentation'")
                .valueExpression("aql:self.getDocumentationBody()")
                .cellWidgetDescription(this.tableBuilders.newCellTextareaWidgetDescription()
                        .body(this.viewBuilders.newChangeContext()
                                .expression(ServiceMethod.of1(RTVMutationServices::editDocumentation).aqlSelf(NEW_VALUE))
                                .build())
                        .build())
                .build();

        // Displays the linked verification case's procedure ID, but is edited by typing the verification
        // case's declared name (not the procedure ID itself) -- editing this cell links the requirement to a
        // different verification case, entirely via VerificationLink (see that metadata's Javadoc). Zero or
        // more than one matching case rejects the edit rather than guessing.
        var verificationProcedureIdCellDescription = this.tableBuilders.newCellDescription()
                .name("RTV-CellDescription-VerificationProcedureId")
                .preconditionExpression("aql:columnTargetObject == 'VerificationProcedureId'")
                .valueExpression(ServiceMethod.of0(RTVQueryServices::getVerificationProcedureId).aqlSelf())
                .cellWidgetDescription(this.tableBuilders.newCellTextfieldWidgetDescription()
                        .body(this.viewBuilders.newChangeContext()
                                .expression(ServiceMethod.of2(RTVMutationServices::setVerificationLink).aqlSelf(NEW_VALUE, IEditingContext.EDITING_CONTEXT))
                                .build())
                        .build())
                .build();

        cells.add(declaredNameCellDescription);
        cells.add(reqIdCellDescription);
        cells.add(docCellDescription);
        cells.add(verificationProcedureIdCellDescription);

        return cells;
    }

    private List<CellDescription> createRequirementAttributeCells() {
        List<CellDescription> cells = new ArrayList<>();

        // Displays the current parents' reqIds, comma-separated; edited by typing a comma-separated list of
        // parent reqIds, entirely via a Dependency relationship (see RTVQueryServices.getParentRequirementIds's
        // Javadoc). Any identifier that fails to resolve to exactly one other requirement rejects the whole edit.
        var parentRequirementsCellDescription = this.tableBuilders.newCellDescription()
                .name("RTV-CellDescription-ParentRequirements")
                .preconditionExpression("aql:columnTargetObject == 'ParentRequirements'")
                .valueExpression(ServiceMethod.of0(RTVQueryServices::getParentRequirementIds).aqlSelf())
                .cellWidgetDescription(this.tableBuilders.newCellTextfieldWidgetDescription()
                        .body(this.viewBuilders.newChangeContext()
                                .expression(ServiceMethod.of2(RTVMutationServices::setParentRequirements).aqlSelf(NEW_VALUE, IEditingContext.EDITING_CONTEXT))
                                .build())
                        .build())
                .build();

        // Classifies the requirement by specializing one of the standard library's Requirements::*RequirementCheck
        // definitions (see RTVRequirementTypeMapping's Javadoc) -- accepts Functional, Interface, Performance,
        // Physical, or Design Constraint, case-insensitively.
        var requirementTypeCellDescription = this.tableBuilders.newCellDescription()
                .name("RTV-CellDescription-RequirementType")
                .preconditionExpression("aql:columnTargetObject == 'RequirementType'")
                .valueExpression(ServiceMethod.of0(RTVQueryServices::getRequirementType).aqlSelf())
                .cellWidgetDescription(this.tableBuilders.newCellTextfieldWidgetDescription()
                        .body(this.viewBuilders.newChangeContext()
                                .expression(ServiceMethod.of1(RTVMutationServices::setRequirementType).aqlSelf(NEW_VALUE))
                                .build())
                        .build())
                .build();

        // Accepts Threshold/Objective (or the underlying threshold/objective literal), case-insensitively, via
        // this project's ThresholdOrObjective metadata (see RequirementAttributesLibrary's Javadoc).
        var thresholdOrObjectiveCellDescription = this.tableBuilders.newCellDescription()
                .name("RTV-CellDescription-ThresholdOrObjective")
                .preconditionExpression("aql:columnTargetObject == 'ThresholdOrObjective'")
                .valueExpression(ServiceMethod.of0(RTVQueryServices::getThresholdOrObjective).aqlSelf())
                .cellWidgetDescription(this.tableBuilders.newCellTextfieldWidgetDescription()
                        .body(this.viewBuilders.newChangeContext()
                                .expression(ServiceMethod.of2(RTVMutationServices::setThresholdOrObjective).aqlSelf(NEW_VALUE, IEditingContext.EDITING_CONTEXT))
                                .build())
                        .build())
                .build();

        var categoryCellDescription = this.tableBuilders.newCellDescription()
                .name("RTV-CellDescription-Category")
                .preconditionExpression("aql:columnTargetObject == 'Category'")
                .valueExpression(ServiceMethod.of0(RTVQueryServices::getCategory).aqlSelf())
                .cellWidgetDescription(this.tableBuilders.newCellTextfieldWidgetDescription()
                        .body(this.viewBuilders.newChangeContext()
                                .expression(ServiceMethod.of2(RTVMutationServices::setCategory).aqlSelf(NEW_VALUE, IEditingContext.EDITING_CONTEXT))
                                .build())
                        .build())
                .build();

        cells.add(parentRequirementsCellDescription);
        cells.add(requirementTypeCellDescription);
        cells.add(thresholdOrObjectiveCellDescription);
        cells.add(categoryCellDescription);

        return cells;
    }

    private List<RowContextMenuEntry> createContextMenuEntries() {
        List<RowContextMenuEntry> entries = new ArrayList<>();

        var removeContextMenuEntry = this.tableBuilders.newRowContextMenuEntry()
                .name("RTV-ContextMenuEntry-RemoveRequirement")
                .labelExpression("Delete from table")
                .iconURLExpression("/images/graphicalDelete.svg")
                .body(this.viewBuilders.newChangeContext()
                        .expression(ServiceMethod.of2(RTVMutationServices::removeFromExposedElements).aqlSelf(IEditingContext.EDITING_CONTEXT, TableDescription.TABLE))
                        .build())
                .build();

        var deleteContextMenuEntry = this.tableBuilders.newRowContextMenuEntry()
                .name("RTV-ContextMenuEntry-DeleteRequirement")
                .labelExpression("Delete from model")
                .iconURLExpression("/images/semanticDelete.svg")
                .body(this.viewBuilders.newChangeContext()
                        .expression(ServiceMethod.of0(DeleteService::deleteFromModel).aqlSelf())
                        .build())
                .build();

        var newNestedRequirementContextMenuEntry = this.tableBuilders.newRowContextMenuEntry()
                .name("RTV-ContextMenuEntry-NewNestedRequirement")
                .labelExpression("New Nested Requirement")
                .iconURLExpression("/images/createRequirement.svg")
                .body(this.viewBuilders.newChangeContext()
                        .expression(ServiceMethod.of2(RTVMutationServices::createNestedRequirement).aqlSelf(IEditingContext.EDITING_CONTEXT, TableDescription.TABLE))
                        .build())
                .build();

        entries.add(removeContextMenuEntry);
        entries.add(deleteContextMenuEntry);
        entries.add(newNestedRequirementContextMenuEntry);

        return entries;
    }
}
