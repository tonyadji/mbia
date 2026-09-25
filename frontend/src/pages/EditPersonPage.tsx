import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Button, buttonClassName } from '../components/Button';
import { i18n } from '../i18n';
import {
  BiographyField,
  LifeFields,
  NameFields,
  OtherIdentityFields,
  toFormValues,
  toPersonFields,
  type PersonFormValues,
} from '../persons/PersonFormFields';
import { useUpdatePerson } from '../persons/useUpdatePerson';
import type { Person } from '../persons/usePerson';
import { PersonRoute, canEditPerson, displayNameOf, personPath } from './PersonProfilePage';

/**
 * SCREEN-012 — Edit Person, for ADMIN / CONTRIBUTOR. The form sends the version it was loaded
 * with; when someone changed the Person meanwhile, the User reloads the latest version before
 * retrying. Form values are never merged automatically.
 */
export function EditPersonPage() {
  return (
    <PersonRoute>
      {({ familyId, person, role, reload }) =>
        canEditPerson(person, role) ? (
          <EditPersonForm familyId={familyId} loaded={person} reload={reload} />
        ) : (
          <Navigate to={personPath(familyId, person.id)} replace />
        )
      }
    </PersonRoute>
  );
}

function EditPersonForm({
  familyId,
  loaded,
  reload,
}: {
  familyId: string;
  loaded: Person;
  reload: () => Promise<Person | undefined>;
}) {
  const { t } = useTranslation(['person', 'settings']);
  const navigate = useNavigate();
  // The version this form was built from; a background refresh of the Person does not change it.
  const [base, setBase] = useState(loaded);
  const form = useForm<PersonFormValues>({ defaultValues: toFormValues(base) });
  const update = useUpdatePerson(familyId, base.id);
  const profile = personPath(familyId, base.id);
  const isConflict =
    update.error instanceof ApiError && update.error.code === 'CONCURRENT_MODIFICATION';

  const submit = form.handleSubmit((values) => {
    update.mutate(
      { version: base.version, body: toPersonFields(values) },
      { onSuccess: () => void navigate(profile) },
    );
  });

  const reloadLatest = async () => {
    const latest = await reload();
    if (latest) {
      setBase(latest);
      form.reset(toFormValues(latest));
      update.reset();
    }
  };

  return (
    <div className="flex flex-1 flex-col gap-8">
      <header className="flex flex-col gap-4">
        <Link
          to={profile}
          className="self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('settings:back')}
        </Link>
        <h1 className="text-display break-words text-text">
          {t('person:edit.title', { name: displayNameOf(base) })}
        </h1>
      </header>
      <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-8">
        <section aria-labelledby="edit-identity" className="flex flex-col gap-4">
          <h2 id="edit-identity" className="text-section text-text">
            {t('person:edit.identity')}
          </h2>
          <NameFields form={form} autoComplete={false} />
          <OtherIdentityFields form={form} />
        </section>
        <section aria-labelledby="edit-life" className="flex flex-col gap-4">
          <h2 id="edit-life" className="text-section text-text">
            {t('person:edit.life')}
          </h2>
          <LifeFields form={form} />
        </section>
        <section aria-labelledby="edit-about" className="flex flex-col gap-4">
          <h2 id="edit-about" className="text-section text-text">
            {t('person:edit.about')}
          </h2>
          <BiographyField form={form} />
        </section>

        {isConflict ? (
          <div
            role="alert"
            className="flex flex-col gap-3 rounded-xl border border-border bg-surface px-4 py-3"
          >
            <p className="text-body">{t('person:edit.conflict')}</p>
            <Button variant="secondary" onClick={() => void reloadLatest()}>
              {t('person:edit.reload')}
            </Button>
          </div>
        ) : (
          update.isError && <p role="alert">{errorMessage(i18n, update.error)}</p>
        )}
        <div className="flex flex-col gap-3 sm:flex-row">
          <Button type="submit" disabled={update.isPending || isConflict} className="sm:w-auto">
            {t('person:edit.submit')}
          </Button>
          <Link to={profile} className={buttonClassName('secondary', 'sm:w-auto')}>
            {t('person:edit.cancel')}
          </Link>
        </div>
      </form>
    </div>
  );
}
