import { KEYCLOAK_URL } from './keycloak';
import { MAILPIT_URL } from './mailpit';

const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080';

const services = [
  { name: 'backend', url: `${API_URL}/actuator/health` },
  { name: 'Keycloak', url: `${KEYCLOAK_URL}/.well-known/openid-configuration` },
  { name: 'Mailpit', url: `${MAILPIT_URL}/api/v1/info` },
];

/** Fails fast, with the commands to run, when a service the tests need is not up. */
export default async function globalSetup() {
  const down: string[] = [];
  for (const { name, url } of services) {
    const up = await fetch(url).then(
      (response) => response.ok,
      () => false,
    );
    if (!up) down.push(`${name} (${url})`);
  }
  if (down.length > 0) {
    throw new Error(
      `E2E services not reachable: ${down.join(', ')}.\n` +
        'Start them first: `docker compose up -d`, then ' +
        '`cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`.',
    );
  }
}
