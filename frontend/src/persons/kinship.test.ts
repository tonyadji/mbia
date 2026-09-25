import { i18n } from '../i18n';
import { genderForm, kinshipLabel, kinshipPathSentences, type KinshipCode } from './kinship';

const fr = i18n.getFixedT('fr', 'person');
const en = i18n.getFixedT('en', 'person');

type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'UNKNOWN';

// localization-and-kinship-labels.md §3: code, target gender, French, English.
const LABELS: [KinshipCode, Gender, string, string][] = [
  ['SELF', 'MALE', 'Vous', 'You'],
  ['SELF', 'FEMALE', 'Vous', 'You'],
  ['FATHER', 'MALE', 'Votre père', 'Your father'],
  ['MOTHER', 'FEMALE', 'Votre mère', 'Your mother'],
  ['PARENT', 'OTHER', 'Votre parent', 'Your parent'],
  ['SON', 'MALE', 'Votre fils', 'Your son'],
  ['DAUGHTER', 'FEMALE', 'Votre fille', 'Your daughter'],
  ['CHILD', 'UNKNOWN', 'Votre enfant', 'Your child'],
  ['GRANDFATHER', 'MALE', 'Votre grand-père', 'Your grandfather'],
  ['GRANDMOTHER', 'FEMALE', 'Votre grand-mère', 'Your grandmother'],
  ['GRANDPARENT', 'OTHER', 'Votre grand-parent', 'Your grandparent'],
  ['GRANDSON', 'MALE', 'Votre petit-fils', 'Your grandson'],
  ['GRANDDAUGHTER', 'FEMALE', 'Votre petite-fille', 'Your granddaughter'],
  ['GRANDCHILD', 'UNKNOWN', 'Votre petit-enfant', 'Your grandchild'],
  ['BROTHER', 'MALE', 'Votre frère', 'Your brother'],
  ['SISTER', 'FEMALE', 'Votre sœur', 'Your sister'],
  ['SIBLING', 'OTHER', 'Votre frère ou sœur', 'Your sibling'],
  ['UNCLE', 'MALE', 'Votre oncle', 'Your uncle'],
  ['AUNT', 'FEMALE', 'Votre tante', 'Your aunt'],
  ['PARENT_SIBLING', 'UNKNOWN', 'Frère ou sœur de votre parent', "Your parent's sibling"],
  ['NEPHEW', 'MALE', 'Votre neveu', 'Your nephew'],
  ['NIECE', 'FEMALE', 'Votre nièce', 'Your niece'],
  ['SIBLING_CHILD', 'OTHER', 'Enfant de votre frère ou sœur', "Your sibling's child"],
  ['FIRST_COUSIN', 'MALE', 'Votre cousin', 'Your first cousin'],
  ['FIRST_COUSIN', 'FEMALE', 'Votre cousine', 'Your first cousin'],
  ['FIRST_COUSIN', 'OTHER', 'Votre cousin ou cousine', 'Your first cousin'],
  ['FIRST_COUSIN', 'UNKNOWN', 'Votre cousin ou cousine', 'Your first cousin'],
  ['PARTNER', 'MALE', 'Votre partenaire', 'Your partner'],
  ['PARTNER', 'FEMALE', 'Votre partenaire', 'Your partner'],
  ['PARTNER', 'UNKNOWN', 'Votre partenaire', 'Your partner'],
  ['RELATED', 'FEMALE', 'Membre de votre famille', 'Your relative'],
];

describe('kinshipLabel', () => {
  it.each(LABELS)('labels %s (%s) as in the specification', (code, gender, french, english) => {
    expect(kinshipLabel(fr, code, gender)).toBe(french);
    expect(kinshipLabel(en, code, gender)).toBe(english);
  });

  it('gives no badge when no relationship is known', () => {
    expect(kinshipLabel(fr, 'NONE_KNOWN', 'MALE')).toBeNull();
    expect(kinshipLabel(en, 'NONE_KNOWN', 'UNKNOWN')).toBeNull();
  });

  it('never calls a partner a husband or a wife', () => {
    for (const t of [fr, en]) {
      for (const gender of ['MALE', 'FEMALE'] as const) {
        expect(kinshipLabel(t, 'PARTNER', gender)).not.toMatch(/mari|épouse|husband|wife/i);
      }
    }
  });
});

describe('genderForm', () => {
  it('uses the neutral form for OTHER and UNKNOWN', () => {
    expect(genderForm('MALE')).toBe('male');
    expect(genderForm('FEMALE')).toBe('female');
    expect(genderForm('OTHER')).toBe('neutral');
    expect(genderForm('UNKNOWN')).toBe('neutral');
  });
});

describe('kinshipPathSentences', () => {
  const persons = {
    tony: { displayName: 'Tony', gender: 'MALE' as const },
    marie: { displayName: 'Marie', gender: 'FEMALE' as const },
    paul: { displayName: 'Paul', gender: 'MALE' as const },
    alex: { displayName: 'Alex', gender: 'OTHER' as const },
  };
  const step = (
    fromPersonId: string,
    toPersonId: string,
    relation: 'PARENT' | 'CHILD' | 'PARTNER',
  ) => ({
    fromPersonId,
    toPersonId,
    relation,
  });

  it('explains the grandfather example of the specification', () => {
    const path = [step('tony', 'marie', 'PARENT'), step('marie', 'paul', 'PARENT')];

    expect(kinshipPathSentences(fr, path, persons)).toEqual([
      'Marie est la mère de Tony',
      'Paul est le père de Marie',
    ]);
    expect(kinshipPathSentences(en, path, persons)).toEqual([
      "Marie is Tony's mother",
      "Paul is Marie's father",
    ]);
  });

  // localization-and-kinship-labels.md §4: relation, `to` Person, French, English.
  it.each([
    ['PARENT', 'paul', 'Paul est le père de Tony', "Paul is Tony's father"],
    ['PARENT', 'marie', 'Marie est la mère de Tony', "Marie is Tony's mother"],
    ['PARENT', 'alex', 'Alex est un parent de Tony', "Alex is Tony's parent"],
    ['CHILD', 'paul', 'Paul est le fils de Tony', "Paul is Tony's son"],
    ['CHILD', 'marie', 'Marie est la fille de Tony', "Marie is Tony's daughter"],
    ['CHILD', 'alex', 'Alex est un enfant de Tony', "Alex is Tony's child"],
    ['PARTNER', 'paul', 'Paul est le partenaire de Tony', "Paul is Tony's partner"],
    ['PARTNER', 'marie', 'Marie est la partenaire de Tony', "Marie is Tony's partner"],
    ['PARTNER', 'alex', 'Alex est partenaire de Tony', "Alex is Tony's partner"],
  ] as const)(
    'renders a %s step to %s gendered by the `to` Person',
    (relation, to, french, english) => {
      const path = [step('tony', to, relation)];

      expect(kinshipPathSentences(fr, path, persons)).toEqual([french]);
      expect(kinshipPathSentences(en, path, persons)).toEqual([english]);
    },
  );
});
