import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { errorMessage } from '../api/errorMessage';
import { HOME_PATH, useAuth } from '../auth/AuthProvider';
import { accountPageUrl } from '../auth/oidcConfig';
import { useCurrentUser } from '../auth/useCurrentUser';
import { useUpdateCurrentUser } from '../auth/useUpdateCurrentUser';
import { Button, buttonClassName } from '../components/Button';
import { Select } from '../components/Select';
import { TextField } from '../components/TextField';
import { i18n } from '../i18n';
import { SUPPORTED_LANGUAGES, isSupportedLanguage, type Language } from '../i18n/language';

/** `UpdateCurrentUserRequest.displayName.maxLength` in openapi.yaml. */
export const DISPLAY_NAME_MAX_LENGTH = 200;

export const SETTINGS_PATH = '/settings';

/** SCREEN-011 — Account settings (terms, privacy and support links come in a later phase). */
export function AccountSettingsPage() {
  const { t } = useTranslation(['settings', 'auth']);
  const { signOut } = useAuth();
  const currentUser = useCurrentUser().data;

  // Rendered below ProtectedRoute, which shows this page only once the User is loaded.
  if (currentUser === undefined) return null;

  return (
    <div className="flex flex-1 flex-col gap-8">
      <header className="flex flex-col gap-4">
        <Link
          to={HOME_PATH}
          className="inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('back')}
        </Link>
        <h1 className="text-display text-text">{t('title')}</h1>
      </header>

      <DisplayNameForm displayName={currentUser.displayName ?? ''} />

      <section className="flex flex-col gap-1">
        <h2 className="text-caption font-semibold text-text">{t('email.label')}</h2>
        <p className="text-body break-all text-text">{currentUser.email}</p>
        <p className="text-caption text-text-muted">{t('email.managed')}</p>
      </section>

      <LanguageSelect />

      <section className="flex flex-col gap-3">
        <h2 className="text-section text-text">{t('security.title')}</h2>
        <a href={accountPageUrl()} className={buttonClassName('secondary')}>
          {t('security.changePassword')}
        </a>
        <Button variant="secondary" onClick={() => void signOut()}>
          {t('auth:signOut')}
        </Button>
      </section>

      <DeleteAccountSection />
    </div>
  );
}

type DisplayNameError = 'required' | 'tooLong';

function DisplayNameForm({ displayName }: { displayName: string }) {
  const { t } = useTranslation('settings');
  const update = useUpdateCurrentUser();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm({ defaultValues: { displayName } });

  const validate = (value: string): true | DisplayNameError => {
    const name = value.trim();
    if (name === '') return 'required';
    if (name.length > DISPLAY_NAME_MAX_LENGTH) return 'tooLong';
    return true;
  };

  const save = handleSubmit((values) => {
    const name = values.displayName.trim();
    update.mutate(
      { displayName: name },
      {
        onSuccess: () => {
          reset({ displayName: name });
        },
      },
    );
  });

  // The error holds a key, so its message follows a language change.
  const errorKey = errors.displayName?.message as DisplayNameError | undefined;
  const fieldError =
    errorKey === 'required'
      ? t('displayName.required')
      : errorKey === 'tooLong'
        ? t('displayName.tooLong', { max: DISPLAY_NAME_MAX_LENGTH })
        : undefined;

  return (
    <form noValidate onSubmit={(event) => void save(event)} className="flex flex-col gap-3">
      <TextField
        label={t('displayName.label')}
        autoComplete="name"
        error={fieldError}
        {...register('displayName', { validate })}
      />
      {update.isError && <p role="alert">{errorMessage(i18n, update.error)}</p>}
      {update.isSuccess && !isDirty && (
        <p role="status" className="text-caption text-text-muted">
          {t('displayName.saved')}
        </p>
      )}
      <Button type="submit" disabled={update.isPending}>
        {t('displayName.save')}
      </Button>
    </form>
  );
}

function LanguageSelect() {
  const { t, i18n: activeI18n } = useTranslation('settings');
  const update = useUpdateCurrentUser();
  const [failed, setFailed] = useState<unknown>(null);
  const language = activeI18n.resolvedLanguage ?? activeI18n.language;

  const change = async (next: Language) => {
    const previous = language;
    setFailed(null);
    // The UI switches at once; the choice is then stored on the User (localization §1).
    await activeI18n.changeLanguage(next);
    update.mutate(
      { preferredLocale: next },
      {
        onError: (error) => {
          setFailed(error);
          void activeI18n.changeLanguage(previous);
        },
      },
    );
  };

  return (
    <div className="flex flex-col gap-2">
      <Select
        label={t('language.label')}
        value={language}
        disabled={update.isPending}
        options={SUPPORTED_LANGUAGES.map((value) => ({ value, label: t(`language.${value}`) }))}
        onChange={(event) => {
          if (isSupportedLanguage(event.target.value)) void change(event.target.value);
        }}
      />
      {failed !== null && <p role="alert">{errorMessage(i18n, failed)}</p>}
    </div>
  );
}

function DeleteAccountSection() {
  const { t } = useTranslation('settings');
  const supportEmail = import.meta.env.VITE_SUPPORT_EMAIL?.trim();

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-section text-text">{t('deleteAccount.title')}</h2>
      <p className="text-body text-text-muted">{t('deleteAccount.explanation')}</p>
      <p className="text-body text-text-muted">{t('deleteAccount.contentKept')}</p>
      {supportEmail ? (
        <a
          href={`mailto:${supportEmail}?subject=${encodeURIComponent(t('deleteAccount.emailSubject'))}`}
          className={buttonClassName('secondary')}
        >
          {t('deleteAccount.contact')}
        </a>
      ) : (
        <p className="text-caption text-text-muted">{t('deleteAccount.noContact')}</p>
      )}
    </section>
  );
}
