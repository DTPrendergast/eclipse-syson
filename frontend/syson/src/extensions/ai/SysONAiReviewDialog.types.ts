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
  /**
   * The element to work from. When given, the dialog uses it instead of the explorer selection -
   * which is the point of opening the assistant from an element's own context menu, where there is
   * no ambiguity about what was meant.
   */
  elementId?: string;
  /** Preselected task, for an entry that exists to run one particular task. */
  initialTask?: string;
  /** Overrides the dialog title when opened for one task. */
  title?: string;
}

export interface GQLProposedItem {
  id: string;
  label: string;
  sysmlFragment: string;
}

export interface GQLGenerateAiProposalPayload {
  __typename: string;
}

export interface GQLGenerateAiProposalSuccessPayload extends GQLGenerateAiProposalPayload {
  proposalId: string | null;
  candidateCount: number;
  items: GQLProposedItem[];
  problems: string[];
}

export interface GQLErrorPayload extends GQLGenerateAiProposalPayload {
  message: string;
}

export interface GQLGenerateAiProposalData {
  generateAiProposal: GQLGenerateAiProposalPayload;
}

export interface GQLGenerateAiProposalVariables {
  input: {
    id: string;
    editingContextId: string;
    elementIds: string[];
    task: string;
    instruction: string;
  };
}

export interface GQLApplyAiProposalItemData {
  applyAiProposalItem: GQLGenerateAiProposalPayload;
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

export interface GQLRejectAiProposalItemData {
  rejectAiProposalItem: GQLGenerateAiProposalPayload;
}

export interface GQLRejectAiProposalItemVariables {
  input: {
    id: string;
    proposalId: string;
    itemId: string;
  };
}

export interface GQLAiProposalItem {
  id: string;
  label: string;
  sysmlFragment: string;
  status: string;
}

export interface GQLAiProposal {
  id: string;
  taskKind: string;
  seedElementIds: string[];
  prompt: string;
  modelId: string;
  createdBy: string;
  createdOn: string;
  items: GQLAiProposalItem[];
}

export interface GQLPendingAiProposalsData {
  viewer: {
    editingContext: {
      pendingAiProposals: GQLAiProposal[];
    } | null;
  };
}

export interface GQLPendingAiProposalsVariables {
  editingContextId: string;
}

/**
 * The assistant tasks offered in the dialog. The `kind` values must match the task kinds the
 * backend registers; an unknown one is reported rather than silently defaulted.
 */
export interface AiTaskOption {
  kind: string;
  label: string;
  hint: string;
}

export const AI_TASKS: AiTaskOption[] = [
  {
    kind: 'derived-requirements',
    label: 'Derive requirements',
    hint: 'Proposes requirements derived from the selected requirements and structure.',
  },
  {
    kind: 'draft-use-cases',
    label: 'Draft use cases',
    hint: 'Drafts use cases from an operational concept narrative.',
  },
  {
    kind: 'draft-narrative',
    label: 'Narrative AI Assistant',
    hint: 'Rewrites an operational concept narrative from notes into prose. Accepting REPLACES the current narrative.',
  },
  {
    kind: 'detail-use-case',
    label: 'Use Case AI Assistant',
    hint: "Fills in one use case's details: actor, trigger, flow and outcome. Accepting REPLACES that use case's documentation.",
  },
];

/**
 * Tasks whose candidate replaces an existing document rather than adding to the model.
 *
 * Worth saying out loud in the dialog: a request about one section still returns - and applies -
 * the entire document, and a reviewer who reads only the changed part can accept a response that
 * silently drops the rest.
 */
export const REPLACING_TASKS: string[] = ['draft-narrative', 'detail-use-case'];

/**
 * Where a candidate has got to in this dialog. "rejected" is local: rejecting a candidate simply
 * means never applying it, and nothing about the model changes.
 */
export type ItemDecision = 'pending' | 'applied' | 'rejected' | 'failed';
