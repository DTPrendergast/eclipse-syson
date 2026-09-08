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
export interface SysONAiReviewDialogProps {
  editingContextId: string;
  open: boolean;
  onClose: () => void;
}

export interface GQLProposedItem {
  id: string;
  label: string;
  sysmlFragment: string;
}

export interface GQLGenerateDerivedRequirementsPayload {
  __typename: string;
}

export interface GQLGenerateDerivedRequirementsSuccessPayload extends GQLGenerateDerivedRequirementsPayload {
  proposalId: string | null;
  candidateCount: number;
  items: GQLProposedItem[];
  problems: string[];
}

export interface GQLErrorPayload extends GQLGenerateDerivedRequirementsPayload {
  message: string;
}

export interface GQLGenerateDerivedRequirementsData {
  generateDerivedRequirements: GQLGenerateDerivedRequirementsPayload;
}

export interface GQLGenerateDerivedRequirementsVariables {
  input: {
    id: string;
    editingContextId: string;
    elementIds: string[];
    instruction: string;
  };
}

export interface GQLApplyAiProposalItemData {
  applyAiProposalItem: GQLGenerateDerivedRequirementsPayload;
}

export interface GQLApplyAiProposalItemVariables {
  input: {
    id: string;
    editingContextId: string;
    proposalId: string;
    itemId: string;
    parentElementId: string;
  };
}

/**
 * Where a candidate has got to in this dialog. "rejected" is local: rejecting a candidate simply
 * means never applying it, and nothing about the model changes.
 */
export type ItemDecision = 'pending' | 'applied' | 'rejected' | 'failed';
