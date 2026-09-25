# Mbia MVP — Localization & Kinship Labels

**Version:** 0.1  
**Status:** Draft

## 1. Supported languages

The MVP ships in two languages:

```text
fr  (French)   — default
en  (English)
```

Rules:

- every user-facing string (UI, emails, error messages, Keycloak login/registration pages) exists in both languages;
- no user-facing string is hard-coded in components; all go through translation keys;
- the initial language is taken from the browser (`fr*` → `fr`, anything else → `en`);
- the User can change language from account settings; the choice is stored on the User (`preferredLocale`) and wins over the browser language on every device;
- transactional emails are sent in the recipient's language when known, otherwise in the language chosen by the sender (invitations use the inviter's current language);
- dates are formatted according to the active language (`12 mars 1954` / `March 12, 1954`); a `YEAR_ONLY` date shows only the year;
- the product term for Memory is **Souvenir** in French and **Memory** in English.

User-entered content (names, stories, captions) is never translated.

## 2. Kinship label source

The API returns a `KinshipCode` (see `technical/api/openapi.yaml`).

A kinship code always describes **what the target Person is to the reference Person**:

```text
kinship(from = Tony, to = Paul) = GRANDFATHER
→ "Paul is Tony's grandfather"
```

`relationshipToCurrentUser` on a Person is `kinship(from = current User's linked Person, to = that Person)`. It is `null` when the current User has no linked Person in the Family.

The server returns gendered codes (`FATHER`, `MOTHER`, …) when the target Person's gender is `MALE` or `FEMALE`, and neutral codes (`PARENT`, …) when it is `OTHER` or `UNKNOWN`.

For codes without gendered variants (`FIRST_COUSIN`, `PARTNER`), the frontend picks the label form from the target Person's `gender`.

## 3. Labels relative to the current User

Used for badges ("Votre grand-père"), quick view, profile header and search results.

| Code | FR — male | FR — female | FR — neutral (OTHER/UNKNOWN) | EN |
|---|---|---|---|---|
| SELF | Vous | Vous | Vous | You |
| FATHER / MOTHER / PARENT | Votre père | Votre mère | Votre parent | Your father / mother / parent |
| SON / DAUGHTER / CHILD | Votre fils | Votre fille | Votre enfant | Your son / daughter / child |
| GRANDFATHER / GRANDMOTHER / GRANDPARENT | Votre grand-père | Votre grand-mère | Votre grand-parent | Your grandfather / grandmother / grandparent |
| GRANDSON / GRANDDAUGHTER / GRANDCHILD | Votre petit-fils | Votre petite-fille | Votre petit-enfant | Your grandson / granddaughter / grandchild |
| BROTHER / SISTER / SIBLING | Votre frère | Votre sœur | Votre frère ou sœur | Your brother / sister / sibling |
| UNCLE / AUNT / PARENT_SIBLING | Votre oncle | Votre tante | Frère ou sœur de votre parent | Your uncle / aunt / parent's sibling |
| NEPHEW / NIECE / SIBLING_CHILD | Votre neveu | Votre nièce | Enfant de votre frère ou sœur | Your nephew / niece / sibling's child |
| FIRST_COUSIN | Votre cousin | Votre cousine | Votre cousin ou cousine | Your first cousin |
| PARTNER | Votre partenaire | Votre partenaire | Votre partenaire | Your partner |
| RELATED | Membre de votre famille | Membre de votre famille | Membre de votre famille | Your relative |
| NONE_KNOWN | *(no badge)* | *(no badge)* | *(no badge)* | *(no badge)* |

Rules:

- `PARTNER` never becomes "mari", "épouse", "husband" or "wife": the MVP does not model marriage.
- Half-siblings are labelled as siblings in the MVP.
- `RELATED` means a path exists but no supported concise label (for example in-laws or second cousins). The UI shows the label and offers "Voir le lien" / "See how" to display the path.
- Never invent a label that is not in this table (the mockup's "Cousin éloigné" is out of scope and shows as `RELATED`).
- On the profile, `NONE_KNOWN` may be explained as: FR "Aucun lien connu dans Mbia pour l'instant" / EN "No known relationship in Mbia yet". Never state that the people are unrelated.

## 4. Path explanation

A `KinshipPathStep` `{fromPersonId, toPersonId, relation}` means: *`to` is the `relation` of `from`*.

Each step is rendered as one sentence, gendered by the `to` Person:

| relation | FR — male | FR — female | FR — neutral | EN |
|---|---|---|---|---|
| PARENT | {to} est le père de {from} | {to} est la mère de {from} | {to} est un parent de {from} | {to} is {from}'s father / mother / parent |
| CHILD | {to} est le fils de {from} | {to} est la fille de {from} | {to} est un enfant de {from} | {to} is {from}'s son / daughter / child |
| PARTNER | {to} est le partenaire de {from} | {to} est la partenaire de {from} | {to} est partenaire de {from} | {to} is {from}'s partner |

Example (current User = Tony, target = Paul):

```text
Paul est votre grand-père.

Marie est la mère de Tony.
Paul est le père de Marie.
```

When `from` is the current User's linked Person, `{from}` may be replaced by "vous" / "you".

## 5. Error messages

Stable API error codes are translated by the frontend. Each code has one FR and one EN message written in human family language (see `family-tree-ux.md` §14). Messages may interpolate Person display names.
