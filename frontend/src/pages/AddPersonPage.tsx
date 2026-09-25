import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Button } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { Skeleton } from '../components/Skeleton';
import { i18n } from '../i18n';
import {
  BiographyField,
  EMPTY_PERSON_FORM,
  LifeFields,
  NameFields,
  OtherIdentityFields,
  hasOptionalFieldError,
  toPersonFields,
  type PersonFormValues,
} from '../persons/PersonFormFields';
import {
  isParentRelation,
  isRelation,
  presetGender,
  relationshipBetween,
  type Relation,
  type RelationshipWarning,
  type RelativeOrigin,
} from '../persons/relatives';
import { useCreatePerson, type CreatePersonRequest } from '../persons/useCreatePerson';
import { useCreateRelationship } from '../persons/useCreateRelationship';
import { usePerson, type Person } from '../persons/usePerson';
import { useUpdatePerson } from '../persons/useUpdatePerson';
import { familyHomePath, type FamilyHomeState } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { displayNameOf, personPath, type PersonProfileState } from './PersonProfilePage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** "Start with me" (`mode=me`) or a standalone Person (SCREEN-004). */
export function addPersonPath(familyId: string, { startWithMe = false } = {}) {
  return `/families/${familyId}/persons/new${startWithMe ? '?mode=me' : ''}`;
}

/** The Person the new relative is added to, and how they are related. */
interface Relative {
  anchor: Person;
  relation: Relation;
  from: RelativeOrigin;
}

/**
 * SCREEN-004 — Add a Person, in "Start with me", standalone or "Relative of a Person" mode
 * (`relativeOf`, `relation`, `from`). Only first and last names are shown first; other details are
 * behind "More information" (family-tree-ux.md §5). No photo in Phase 2.
 */
export function AddPersonPage() {
  const { familyId = '' } = useParams();
  const [params] = useSearchParams();
  const relativeOf = params.get('relativeOf');
  const relation = params.get('relation');
  if (!UUID.test(familyId)) {
    return <FamilyNotFoundPage />;
  }
  if (relativeOf !== null && UUID.test(relativeOf) && isRelation(relation)) {
    return (
      <RelativeForm
        familyId={familyId}
        anchorId={relativeOf}
        relation={relation}
        from={params.get('from') === 'home' ? 'home' : 'profile'}
      />
    );
  }
  return <AddPersonForm familyId={familyId} startWithMe={params.get('mode') === 'me'} />;
}

/** Loads the anchor Person first: the title names them and the link needs them ACTIVE. */
function RelativeForm({
  familyId,
  anchorId,
  relation,
  from,
}: {
  familyId: string;
  anchorId: string;
  relation: Relation;
  from: RelativeOrigin;
}) {
  const anchor = usePerson(familyId, anchorId);
  if (anchor.error instanceof ApiError && anchor.error.status === 404) {
    return <FamilyNotFoundPage />;
  }
  if (anchor.isError) {
    return <ErrorState error={anchor.error} onRetry={() => void anchor.refetch()} />;
  }
  if (anchor.isPending) {
    return <Skeleton className="h-8 w-64" />;
  }
  return (
    <AddPersonForm
      familyId={familyId}
      startWithMe={false}
      relative={{ anchor: anchor.data, relation, from }}
    />
  );
}

function AddPersonForm({
  familyId,
  startWithMe,
  relative,
}: {
  familyId: string;
  startWithMe: boolean;
  relative?: Relative;
}) {
  const { t } = useTranslation(['person', 'settings']);
  const navigate = useNavigate();
  const createPerson = useCreatePerson(familyId);
  const createRelationship = useCreateRelationship(familyId);
  // The Person created by a first attempt whose link was refused: a new attempt updates them.
  const [created, setCreated] = useState<Person | null>(null);
  const updatePerson = useUpdatePerson(familyId, created?.id ?? '');
  const [warnings, setWarnings] = useState<RelationshipWarning[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [notLinked, setNotLinked] = useState(false);
  const [pending, setPending] = useState(false);
  const [showMore, setShowMore] = useState(false);
  const form = useForm<PersonFormValues>({
    defaultValues: {
      ...EMPTY_PERSON_FORM,
      gender: relative ? presetGender(relative.relation) : EMPTY_PERSON_FORM.gender,
    },
  });

  const anchorName = relative ? displayNameOf(relative.anchor) : '';
  const backPath =
    relative?.from === 'profile'
      ? personPath(familyId, relative.anchor.id)
      : familyHomePath(familyId);

  function done(person: Person) {
    const name = displayNameOf(person);
    if (relative?.from === 'profile') {
      const state: PersonProfileState = { relativeAdded: { name } };
      void navigate(backPath, { replace: true, state });
      return;
    }
    const state: FamilyHomeState = {
      personAdded: {
        id: person.id,
        name,
        self: startWithMe,
        linkedTo: relative ? anchorName : undefined,
      },
    };
    void navigate(familyHomePath(familyId), { replace: true, state });
  }

  /** Links the new Person; date warnings wait for the User's choice (SCREEN-004). */
  async function link(person: Person, confirmWarnings: boolean) {
    if (!relative) {
      done(person);
      return;
    }
    try {
      await createRelationship.mutateAsync(
        relationshipBetween(relative.relation, relative.anchor.id, person.id, confirmWarnings),
      );
      done(person);
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        failure.code === 'RELATIONSHIP_WARNING_CONFIRMATION_REQUIRED'
      ) {
        setWarnings((failure.details?.warnings as RelationshipWarning[] | undefined) ?? []);
      } else {
        setError(failure);
        setNotLinked(true);
      }
    }
  }

  async function save(values: PersonFormValues) {
    setError(null);
    setNotLinked(false);
    setWarnings(null);
    setPending(true);
    try {
      let person = created;
      if (person === null) {
        person = await createPerson.mutateAsync(toRequest(values, startWithMe));
        setCreated(person);
      } else {
        // "Correct information" after a warning: the Person exists, only their details change.
        person = await updatePerson.mutateAsync({
          version: person.version,
          body: toPersonFields(values),
        });
        setCreated(person);
      }
      await link(person, false);
    } catch (failure) {
      setError(failure);
    } finally {
      setPending(false);
    }
  }

  async function confirm() {
    if (created === null) return;
    setWarnings(null);
    setPending(true);
    try {
      await link(created, true);
    } finally {
      setPending(false);
    }
  }

  const submit = form.handleSubmit(
    (values) => void save(values),
    (invalid) => {
      if (hasOptionalFieldError(invalid)) setShowMore(true);
    },
  );

  let title = startWithMe ? t('person:form.titleMe') : t('person:form.title');
  if (relative) {
    title =
      relative.anchor.relationshipToCurrentUser === 'SELF'
        ? t(`person:relative.titleMe.${relative.relation}`)
        : t(`person:relative.title.${relative.relation}`, { name: anchorName });
  }
  let intro = startWithMe ? t('person:form.introMe') : t('person:form.intro');
  if (relative) intro = t('person:relative.intro');

  return (
    <div className="flex flex-1 flex-col gap-8">
      <header className="flex flex-col gap-4">
        <Link
          to={backPath}
          className="self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('settings:back')}
        </Link>
        <h1 className="text-display text-text">{title}</h1>
        <p className="text-body text-text-muted">{intro}</p>
      </header>
      <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
        <NameFields form={form} autoComplete={startWithMe} />

        <button
          type="button"
          aria-expanded={showMore}
          aria-controls="person-more"
          onClick={() => {
            setShowMore((open) => !open);
          }}
          className="flex min-h-12 items-center justify-between rounded-xl text-body font-semibold text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          {t('person:form.more')}
          <ChevronIcon open={showMore} />
        </button>
        <div id="person-more" hidden={!showMore} className="flex flex-col gap-4">
          <OtherIdentityFields form={form} />
          <LifeFields form={form} />
          <BiographyField form={form} />
        </div>

        {relative && warnings && created && (
          <DateWarnings
            warnings={warnings}
            relative={relative}
            newName={displayNameOf(created)}
            disabled={pending}
            onCorrect={() => {
              setWarnings(null);
              setShowMore(true);
            }}
            onConfirm={() => void confirm()}
          />
        )}
        {notLinked && created && relative && (
          <div role="alert" className="flex flex-col gap-1 text-body text-text">
            <p>
              {t('person:relative.notLinked', { name: displayNameOf(created), anchor: anchorName })}
            </p>
            <p>{errorMessage(i18n, error)}</p>
            <Link
              to={personPath(familyId, created.id)}
              className="self-start font-semibold text-primary underline-offset-4 hover:underline"
            >
              {t('person:relative.viewProfile')}
            </Link>
          </div>
        )}
        {error !== null && !notLinked && <p role="alert">{errorMessage(i18n, error)}</p>}
        {!warnings && !notLinked && (
          <Button type="submit" disabled={pending}>
            {startWithMe ? t('person:form.submitMe') : t('person:form.submit')}
          </Button>
        )}
      </form>
    </div>
  );
}

/** The date warnings of SCREEN-004, in family language, with the User's two choices. */
function DateWarnings({
  warnings,
  relative,
  newName,
  disabled,
  onCorrect,
  onConfirm,
}: {
  warnings: RelationshipWarning[];
  relative: Relative;
  newName: string;
  disabled: boolean;
  onCorrect: () => void;
  onConfirm: () => void;
}) {
  const { t } = useTranslation('person');
  const anchorName = displayNameOf(relative.anchor);
  const newIsParent = isParentRelation(relative.relation);
  const parent = newIsParent ? newName : anchorName;
  const child = newIsParent ? anchorName : newName;

  return (
    <div
      role="alert"
      className="flex flex-col gap-3 rounded-xl border border-accent bg-surface px-4 py-3"
    >
      <p className="text-body font-semibold text-text">{t('relative.warnings.title')}</p>
      {warnings.map((warning) => {
        const parentYear = Number(warning.context?.parentBirthYear);
        const childYear = Number(warning.context?.childBirthYear);
        const values = { parent, child, parentYear, childYear, age: childYear - parentYear };
        return (
          <p key={warning.code} className="text-body text-text">
            {warning.code === 'PARENT_BORN_AFTER_CHILD'
              ? t('relative.warnings.PARENT_BORN_AFTER_CHILD', values)
              : t('relative.warnings.IMPLAUSIBLE_PARENT_AGE', values)}
          </p>
        );
      })}
      <div className="flex flex-col gap-3 sm:flex-row">
        <Button variant="secondary" disabled={disabled} onClick={onCorrect}>
          {t('relative.warnings.correct')}
        </Button>
        <Button disabled={disabled} onClick={onConfirm}>
          {t('relative.warnings.confirm')}
        </Button>
      </div>
    </div>
  );
}

/** A blank optional value is left out of the creation. */
function toRequest(values: PersonFormValues, startWithMe: boolean): CreatePersonRequest {
  const fields = toPersonFields(values);
  return {
    ...fields,
    lastName: fields.lastName || undefined,
    middleNames: fields.middleNames || undefined,
    preferredName: fields.preferredName || undefined,
    biography: fields.biography || undefined,
    linkToCurrentUser: startWithMe,
    confirmPossibleDuplicate: false,
  };
}

function ChevronIcon({ open }: { open: boolean }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className={`size-5 transition-transform ${open ? 'rotate-180' : ''}`}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}
