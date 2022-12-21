import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import Backend from "i18next-http-backend";
import { initReactI18next } from "react-i18next";

/* function getLoadPath(): string {
  if (import.meta.env.DEV) {
    return "/locales/{{lng}}/{{ns}}.json";
  }

  return "assets/js/explorer/locales/{{lng}}/{{ns}}.json";
} */

i18n
  .use(Backend)
  .use(LanguageDetector) // detect user language
  .use(initReactI18next) // passes i18n down to react-i18next
  .init({
    fallbackLng: "fr",
    lng: "fr", // language to use, more information here: https://www.i18next.com/overview/configuration-options#languages-namespaces-resources
    // you can use the i18n.changeLanguage function to change the language manually: https://www.i18next.com/overview/api#changelanguage
    // if you're using a language detector, do not define the lng option
    interpolation: {
      escapeValue: false, // react already safes from xss
    },
    /* backend: {
      loadPath: getLoadPath(),
    }, */
  });

export default i18n;
