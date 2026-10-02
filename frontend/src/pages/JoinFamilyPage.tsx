import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Button } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import {
  ClaimConfirmation,
  laterClassName,
  useDescribeCandidate,
} from '../invitations/ClaimConfirmation';
import { PersonSearch } from '../persons/PersonSearch';
import { useClaimPerson } from '../persons/useClaimPerson';
import type { ClaimablePerson } from '../persons/useClaimablePersons';
import { useFamilyTree, type FamilyTree } from '../persons/useFamilyTree';
import type { PersonSummary } from '../persons/usePersonSearch';
import { addPersonPath } from './AddPersonPage';
import { familyHomePath, type Family, type FamilyHomeState } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** After joining, with the Person the invitation was sent for when there is one (OQ-050). */
export function joinFamilyPath(familyId: string, suggestedPersonId?: string) {
  const query = suggestedPersonId
    ? `?${new URLSearchParams({ suggested: suggestedPersonId }).toString()}`
    : '';
  return `/families/${familyId}/join${query}`;
}

/** A Person to confirm as "me", with the version to claim it from and one parent when known. */
interface Candidate {
  person: PersonSummary;
  parentName: string | null;
}

/**
 * After accepting an invitation (SCREEN-010, mvp.md §18): "Are you {displayName}?" for the Person
 * the invitation was sent for while it can be claimed, otherwise or after `No` "Are you already in
 * this tree?"; then Family Home with its welcome (SCREEN-002).
 */
export function JoinFamilyPage() {
  const { familyId = '' } = useParams();
  const [params] = useSearchParams();
  const isValidId = UUID.test(familyId);
  const family = useFamily(familyId, { enabled: isValidId });
  const suggested = params.get('suggested');

  if (!isValidId || (family.error instanceof ApiError && family.error.status === 404)) {
    return <FamilyNotFoundPage />;
  }
  if (family.isError) {
    return <ErrorState error={family.error} onRetry={() => void family.refetch()} />;
  }
  if (family.isPending) return <StepSkeleton />;
  if (family.data.myLinkedPersonId != null) {
    const state: FamilyHomeState = { joined: true };
    return <Navigate to={familyHomePath(familyId)} replace state={state} />;
  }
  return (
    <Onboarding
      family={family.data}
      suggestedId={suggested !== null && UUID.test(suggested) ? suggested : null}
    />
  );
}

function Onboarding({ family, suggestedId }: { family: Family; suggestedId: string | null }) {
  const { t } = useTranslation('invitation');
  const navigate = useNavigate();
  const [step, setStep] = useState<'suggested' | 'search' | 'notInTree'>(
    suggestedId === null ? 'search' : 'suggested',
  );
  const [selected, setSelected] = useState<Candidate | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const describe = useDescribeCandidate();

  function finish() {
    const state: FamilyHomeState = { joined: true };
    void navigate(familyHomePath(family.id), { replace: true, state });
  }

  /** The Person was linked, archived or merged in the meantime: only the search is offered. */
  const unavailable = useCallback(
    (message?: string) => {
      setSelected(null);
      setNotice(message ?? t('onboarding.suggestionGone'));
      setStep('search');
    },
    [t],
  );

  if (selected !== null) {
    return (
      <ConfirmStep
        familyId={family.id}
        candidate={selected}
        onClaimed={finish}
        onNo={() => {
          setSelected(null);
          setStep('search');
        }}
        onLater={finish}
        onUnavailable={unavailable}
      />
    );
  }

  if (step === 'suggested' && suggestedId !== null) {
    return (
      <SuggestedStep
        familyId={family.id}
        personId={suggestedId}
        onClaimed={finish}
        onNo={() => {
          setStep('search');
        }}
        onLater={finish}
        onUnavailable={unavailable}
      />
    );
  }

  if (step === 'notInTree') {
    return (
      <section className="flex flex-1 flex-col gap-6">
        <h1 className="text-display text-text">{t('onboarding.viewerTitle')}</h1>
        <p className="text-body text-text-muted">{t('onboarding.viewerBody')}</p>
        <Button onClick={finish}>{t('onboarding.later')}</Button>
      </section>
    );
  }

  const canAddPersons = family.myRole === 'ADMIN' || family.myRole === 'CONTRIBUTOR';
  return (
    <section className="flex flex-1 flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-display text-text">{t('onboarding.inTreeTitle')}</h1>
        <p className="text-body text-text-muted">{t('onboarding.inTreeBody')}</p>
      </div>
      {notice && (
        <p role="status" className="rounded-xl border border-border bg-surface px-4 py-3 text-body">
          {notice}
        </p>
      )}
      <PersonSearch
        familyId={family.id}
        label={t('onboarding.searchLabel')}
        claimable
        describe={(person) =>
          describe(person, (person as ClaimablePerson).parent?.displayName ?? null)
        }
        onSelect={(person) => {
          setSelected({
            person,
            parentName: (person as ClaimablePerson).parent?.displayName ?? null,
          });
        }}
      />
      <div className="flex flex-col gap-3">
        <Button
          variant="secondary"
          onClick={() => {
            if (!canAddPersons) {
              setStep('notInTree');
              return;
            }
            const state: FamilyHomeState = { joined: true };
            void navigate(addPersonPath(family.id, { startWithMe: true }), { state });
          }}
        >
          {t('onboarding.notInTree')}
        </Button>
        <button type="button" className={laterClassName} onClick={finish}>
          {t('onboarding.later')}
        </button>
      </div>
    </section>
  );
}

/**
 * The suggested Person from the tree centred on them: their version, birth and first parent in the
 * order of the tree (OQ-015), the same parent `listClaimablePersons` shows. A Person no longer
 * ACTIVE is not the focus (the server chose another one), and a linked one cannot be claimed.
 */
function suggestedCandidate(tree: FamilyTree, personId: string): Candidate | null {
  const person = tree.nodes.find((node) => node.id === personId);
  if (tree.focusPersonId !== personId || person === undefined || person.linkedUserId != null) {
    return null;
  }
  const parentIds = new Set(
    tree.edges
      .filter((edge) => edge.type === 'PARENT_OF' && edge.targetPersonId === personId)
      .map((edge) => edge.sourcePersonId),
  );
  const parent = tree.nodes.find((node) => parentIds.has(node.id));
  return { person, parentName: parent ? (parent.displayName ?? parent.firstName) : null };
}

function SuggestedStep({
  familyId,
  personId,
  onUnavailable,
  ...confirm
}: {
  familyId: string;
  personId: string;
  onClaimed: () => void;
  onNo: () => void;
  onLater: () => void;
  onUnavailable: (message?: string) => void;
}) {
  const tree = useFamilyTree(familyId, personId);
  const candidate = tree.data ? suggestedCandidate(tree.data, personId) : undefined;
  const gone = candidate === null;

  useEffect(() => {
    if (gone) onUnavailable();
  }, [gone, onUnavailable]);

  if (tree.isError) {
    return <ErrorState error={tree.error} onRetry={() => void tree.refetch()} />;
  }
  if (candidate == null) return <StepSkeleton />;
  return (
    <ConfirmStep
      familyId={familyId}
      candidate={candidate}
      onUnavailable={onUnavailable}
      {...confirm}
    />
  );
}

function ConfirmStep({
  familyId,
  candidate,
  onClaimed,
  onNo,
  onLater,
  onUnavailable,
}: {
  familyId: string;
  candidate: Candidate;
  onClaimed: () => void;
  onNo: () => void;
  onLater: () => void;
  onUnavailable: (message?: string) => void;
}) {
  const { t, i18n } = useTranslation('invitation');
  const claim = useClaimPerson(familyId, candidate.person.id);
  const name = candidate.person.displayName ?? candidate.person.firstName;

  function yes() {
    claim.mutate(
      { claim: true, version: candidate.person.version },
      {
        onSuccess: onClaimed,
        onError: (error) => {
          if (!(error instanceof ApiError)) return;
          if (error.code === 'USER_ALREADY_LINKED') onClaimed();
          if (
            error.code === 'PERSON_ALREADY_CLAIMED' ||
            error.code === 'PERSON_NOT_FOUND' ||
            error.code === 'CONCURRENT_MODIFICATION'
          ) {
            onUnavailable(t('onboarding.claimGone', { name }));
          }
        },
      },
    );
  }

  return (
    <ClaimConfirmation
      person={candidate.person}
      parentName={candidate.parentName}
      isPending={claim.isPending}
      error={claim.isError ? errorMessage(i18n, claim.error) : null}
      onYes={yes}
      onNo={onNo}
      onLater={onLater}
    />
  );
}

function StepSkeleton() {
  return (
    <div aria-busy="true" className="flex flex-col gap-3">
      <Skeleton className="h-9 w-64" />
      <Skeleton className="h-16" />
      <Skeleton className="h-12" />
    </div>
  );
}
