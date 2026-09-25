import { expect, type APIRequestContext } from '@playwright/test';

export const MAILPIT_URL = process.env.MAILPIT_URL ?? 'http://localhost:8025';

interface SearchResult {
  messages: { ID: string }[];
}

interface Message {
  Text: string;
}

/** The email verification link Keycloak sent to `email`, read through the Mailpit API. */
export async function verificationLink(request: APIRequestContext, email: string): Promise<string> {
  let messageId: string | undefined;
  await expect
    .poll(
      async () => {
        const response = await request.get(`${MAILPIT_URL}/api/v1/search`, {
          params: { query: `to:"${email}"` },
        });
        messageId = ((await response.json()) as SearchResult).messages[0]?.ID;
        return messageId;
      },
      { message: `verification email for ${email}`, timeout: 15_000 },
    )
    .toBeDefined();

  const response = await request.get(`${MAILPIT_URL}/api/v1/message/${String(messageId)}`);
  const { Text } = (await response.json()) as Message;
  const link = /https?:\/\/\S+\/login-actions\/action-token\S+/.exec(Text)?.[0];
  expect(link, `verification link in the email to ${email}`).toBeDefined();
  return String(link);
}
