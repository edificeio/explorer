import i18n from 'i18next';
import Backend from 'i18next-http-backend';
import { initReactI18next } from 'react-i18next';

i18n
  .use(Backend)
  .use(initReactI18next)
  .init({
    backend: {
      loadPath: (_lngs: string[], namespaces: string[]) => {
        const urls = namespaces.map((namespace: string) => {
          if (namespace === 'common') {
            return `/i18n`;
          }
          return `/${namespace}/i18n`;
        });
        return urls;
      },
      parse: (data: string) => JSON.parse(data),
    },
    defaultNS: 'common',
    ns: ['common'],
    fallbackLng: 'fr',
    lng: 'fr',
    interpolation: {
      escapeValue: false,
      prefix: '[[',
      suffix: ']]',
    },
    debug: false,
    react: {
      useSuspense: false,
    },
  });

export default i18n;
