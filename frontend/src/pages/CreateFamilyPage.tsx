import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';
import { errorMessage } from '../api/errorMessage';
import { HOME_PATH } from '../auth/AuthProvider';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { useCreateFamily } from '../families/useCreateFamily';
import { useMyFamilies } from '../families/useMyFamilies';
import { i18n } from '../i18n';
import { familyHomePath, type FamilyHomeState } from './FamilyHomePage';

/** `CreateFamilyRequest.name.maxLength` in openapi.yaml. */
export const FAMILY_NAME_MAX_LENGTH = 200;

type FamilyNameError = 'required' | 'tooLong';

/** Family creation (mvp.md §14): the creator becomes its ADMIN. */
export function CreateFamilyPage() {
  const { t } = useTranslation(['family', 'settings']);
  const navigate = useNavigate();
  const create = useCreateFamily();
  const hasFamilies = (useMyFamilies().data?.length ?? 0) > 0;
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ defaultValues: { name: '' } });

  const validate = (value: string): true | FamilyNameError => {
    const name = value.trim();
    if (name === '') return 'required';
    if (name.length > FAMILY_NAME_MAX_LENGTH) return 'tooLong';
    return true;
  };

  const submit = handleSubmit((values) => {
    create.mutate(values.name.trim(), {
      onSuccess: (family) => {
        const state: FamilyHomeState = { created: true };
        void navigate(familyHomePath(family.id), { replace: true, state });
      },
    });
  });

  // The error holds a key, so its message follows a language change.
  const errorKey = errors.name?.message as FamilyNameError | undefined;
  const fieldError =
    errorKey === 'required'
      ? t('create.nameRequired')
      : errorKey === 'tooLong'
        ? t('create.nameTooLong', { max: FAMILY_NAME_MAX_LENGTH })
        : undefined;

  return (
    <div className="flex flex-1 flex-col gap-8">
      <header className="flex flex-col gap-4">
        {hasFamilies && (
          <Link
            to={HOME_PATH}
            className="inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
          >
            {t('settings:back')}
          </Link>
        )}
        <h1 className="text-display text-text">{t('create.title')}</h1>
        <p className="text-body text-text-muted">{t('create.intro')}</p>
      </header>
      <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
        <TextField
          label={t('create.nameLabel')}
          placeholder={t('create.namePlaceholder')}
          autoComplete="off"
          error={fieldError}
          {...register('name', { validate })}
        />
        {create.isError && <p role="alert">{errorMessage(i18n, create.error)}</p>}
        <Button type="submit" disabled={create.isPending}>
          {t('create.submit')}
        </Button>
      </form>
    </div>
  );
}
