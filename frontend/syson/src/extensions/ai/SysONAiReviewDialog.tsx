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
import { gql, useMutation, useQuery } from '@apollo/client';
import { useSelection } from '@eclipse-sirius/sirius-components-core';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import {
  AI_TASKS,
  REPLACING_TASKS,
  GQLAiProposal,
  GQLApplyAiProposalItemData,
  GQLApplyAiProposalItemVariables,
  GQLRejectAiProposalItemData,
  GQLRejectAiProposalItemVariables,
  GQLGenerateAiProposalData,
  GQLGenerateAiProposalSuccessPayload,
  GQLGenerateAiProposalVariables,
  GQLPendingAiProposalsData,
  GQLPendingAiProposalsVariables,
  GQLProposedItem,
  ItemDecision,
  SysONAiReviewDialogProps,
} from './SysONAiReviewDialog.types';

const generateAiProposalMutation = gql`
  mutation generateAiProposal($input: GenerateAiProposalInput!) {
    generateAiProposal(input: $input) {
      __typename
      ... on GenerateAiProposalSuccessPayload {
        proposalId
        candidateCount
        items {
          id
          label
          sysmlFragment
        }
        problems
      }
      ... on ErrorPayload {
        message
      }
    }
  }
`;

const pendingAiProposalsQuery = gql`
  query pendingAiProposals($editingContextId: ID!) {
    viewer {
      editingContext(editingContextId: $editingContextId) {
        pendingAiProposals {
          id
          taskKind
          seedElementIds
          prompt
          createdOn
          items {
            id
            label
            sysmlFragment
            status
          }
        }
      }
    }
  }
`;

const rejectAiProposalItemMutation = gql`
  mutation rejectAiProposalItem($input: RejectAiProposalItemInput!) {
    rejectAiProposalItem(input: $input) {
      __typename
      ... on ErrorPayload {
        message
      }
    }
  }
`;

const applyAiProposalItemMutation = gql`
  mutation applyAiProposalItem($input: ApplyAiProposalItemInput!) {
    applyAiProposalItem(input: $input) {
      __typename
      ... on ErrorPayload {
        message
      }
    }
  }
`;

/**
 * Review of AI-proposed requirements, accepted or rejected one at a time.
 *
 * Nothing here applies anything on its own: every candidate is shown as the exact textual SysML v2
 * that will be added if it is accepted, and it is added only when someone presses Accept. Rejecting
 * changes nothing in the model - that is what it means - but it is still recorded, because a
 * proposal that is left undecided is one the dialog offers again the next time it opens.
 */
export const SysONAiReviewDialog = ({
  editingContextId,
  open,
  onClose,
  elementId,
  initialTask,
  title,
}: SysONAiReviewDialogProps) => {
  const { selection } = useSelection();
  const [instruction, setInstruction] = useState<string>('');
  const [task, setTask] = useState<string>(initialTask ?? AI_TASKS[0]!.kind);
  const [proposalId, setProposalId] = useState<string | null>(null);
  const [items, setItems] = useState<GQLProposedItem[]>([]);
  const [problems, setProblems] = useState<string[]>([]);
  const [decisions, setDecisions] = useState<Record<string, ItemDecision>>({});

  // An element handed in by a context menu wins over the explorer selection: the menu entry was
  // opened on that element, so there is nothing to infer.
  const selectedIds: string[] = elementId ? [elementId] : selection.entries.map((entry) => entry.id);

  // A generation costs real money and real time, so an unreviewed one is picked back up rather
  // than silently abandoned when the dialog is reopened or the page is refreshed.
  const { data: pendingData } = useQuery<GQLPendingAiProposalsData, GQLPendingAiProposalsVariables>(
    pendingAiProposalsQuery,
    { variables: { editingContextId }, skip: !open, fetchPolicy: 'network-only' }
  );

  const pendingProposals: GQLAiProposal[] = pendingData?.viewer.editingContext?.pendingAiProposals ?? [];

  useEffect(() => {
    if (!open || proposalId !== null || pendingProposals.length === 0) {
      return;
    }
    // Only resume a proposal that was produced by the task in front of the user, from the element
    // in front of them. Without both checks the dialog resurrected any unreviewed proposal in the
    // project - so a ten-day-old use-case run reappeared under a narrative request, and accepting
    // one of its candidates would have applied it to the wrong element.
    const resumable = pendingProposals.filter(
      (proposal) =>
        proposal.taskKind === task &&
        (selectedIds.length === 0 || selectedIds.every((id) => proposal.seedElementIds.includes(id)))
    );
    if (resumable.length === 0) {
      return;
    }
    const mostRecent = resumable[0]!;
    setProposalId(mostRecent.id);
    setItems(mostRecent.items.map((item) => ({ id: item.id, label: item.label, sysmlFragment: item.sysmlFragment })));
    setDecisions(
      Object.fromEntries(
        mostRecent.items.map((item) => [item.id, item.status === 'APPLIED' ? 'applied' : 'pending'] as const)
      )
    );
    // Only prefill an empty box; never overwrite what the user is in the middle of typing.
    setInstruction((current) => (current.trim().length === 0 ? mostRecent.prompt : current));
  }, [open, proposalId, pendingProposals, task, selectedIds]);

  const [generate, { loading: generating }] = useMutation<GQLGenerateAiProposalData, GQLGenerateAiProposalVariables>(
    generateAiProposalMutation
  );

  const [applyItem, { loading: applying }] = useMutation<GQLApplyAiProposalItemData, GQLApplyAiProposalItemVariables>(
    applyAiProposalItemMutation
  );

  const [rejectItem, { loading: rejecting }] = useMutation<GQLRejectAiProposalItemData, GQLRejectAiProposalItemVariables>(
    rejectAiProposalItemMutation
  );

  const onGenerate = () => {
    setProblems([]);
    setItems([]);
    setDecisions({});
    generate({
      variables: {
        input: {
          id: crypto.randomUUID(),
          editingContextId,
          elementIds: selectedIds,
          task,
          instruction,
        },
      },
    }).then(({ data }) => {
      const payload = data?.generateAiProposal;
      if (payload && payload.__typename === 'GenerateAiProposalSuccessPayload') {
        const success = payload as GQLGenerateAiProposalSuccessPayload;
        setProposalId(success.proposalId);
        setItems(success.items);
        setProblems(success.problems);
      } else if (payload && payload.__typename === 'ErrorPayload') {
        setProblems(['The assistant could not be reached.']);
      }
    });
  };

  const onAccept = (item: GQLProposedItem) => {
    if (proposalId === null || selectedIds.length === 0) {
      return;
    }
    applyItem({
      variables: {
        input: {
          id: crypto.randomUUID(),
          editingContextId,
          proposalId,
          itemId: item.id,
          // The new element is created under the first selected element, which is also what the
          // generation was scoped from.
          parentElementId: selectedIds[0]!,
        },
      },
    }).then(({ data }) => {
      const applied = data?.applyAiProposalItem.__typename === 'SuccessPayload';
      setDecisions((current) => ({ ...current, [item.id]: applied ? 'applied' : 'failed' }));
    });
  };

  const onReject = (item: GQLProposedItem) => {
    if (proposalId === null) {
      return;
    }
    rejectItem({
      variables: {
        input: {
          id: crypto.randomUUID(),
          proposalId,
          itemId: item.id,
        },
      },
    }).then(({ data }) => {
      const rejected = data?.rejectAiProposalItem.__typename === 'SuccessPayload';
      setDecisions((current) => ({ ...current, [item.id]: rejected ? 'rejected' : 'failed' }));
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md" data-testid="ai-review-dialog">
      <DialogTitle>{title ?? 'Ask the modeling assistant'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          {proposalId !== null && !generating ? (
            <Alert severity="info">Resumed an earlier proposal that had not been reviewed yet.</Alert>
          ) : null}
          {REPLACING_TASKS.includes(task) ? (
            <Alert severity="warning">
              Accepting replaces the whole document, not just the part you asked about. Review the full text before
              accepting.
            </Alert>
          ) : null}
          <Typography variant="body2" color="textSecondary">
            {selectedIds.length === 0
              ? 'Select one or more elements in the explorer to work from.'
              : elementId
              ? 'Working from the element this menu was opened on.'
              : `Working from ${selectedIds.length} selected element(s).`}
          </Typography>
          {initialTask ? null : (
            <TextField
              select
              label="Task"
              value={task}
              onChange={(event) => setTask(event.target.value)}
              fullWidth
              helperText={AI_TASKS.find((entry) => entry.kind === task)?.hint ?? ''}
              data-testid="ai-task">
              {AI_TASKS.map((entry) => (
                <MenuItem key={entry.kind} value={entry.kind}>
                  {entry.label}
                </MenuItem>
              ))}
            </TextField>
          )}
          <TextField
            label="What should the assistant do?"
            value={instruction}
            onChange={(event) => setInstruction(event.target.value)}
            multiline
            minRows={2}
            fullWidth
            data-testid="ai-instruction"
          />
          {problems.map((problem, index) => (
            <Alert severity="info" key={index}>
              {problem}
            </Alert>
          ))}
          {items.length > 0 ? <Divider /> : null}
          {items.map((item) => {
            const decision: ItemDecision = decisions[item.id] ?? 'pending';
            return (
              <Stack key={item.id} spacing={1} data-testid={`ai-candidate-${item.label}`}>
                <Typography variant="subtitle2">{item.label}</Typography>
                <Typography variant="body2" component="pre" sx={{ whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}>
                  {item.sysmlFragment}
                </Typography>
                {decision === 'pending' ? (
                  <Stack direction="row" spacing={1}>
                    <Button size="small" variant="contained" disabled={applying} onClick={() => onAccept(item)}>
                      Accept
                    </Button>
                    <Button size="small" disabled={rejecting} onClick={() => onReject(item)}>
                      Reject
                    </Button>
                  </Stack>
                ) : (
                  <Typography variant="caption" color={decision === 'failed' ? 'error' : 'textSecondary'}>
                    {decision}
                  </Typography>
                )}
              </Stack>
            );
          })}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          onClick={onGenerate}
          disabled={generating || instruction.trim().length === 0 || selectedIds.length === 0}
          data-testid="ai-generate">
          {generating ? <CircularProgress size={20} /> : 'Propose'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
