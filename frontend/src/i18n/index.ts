import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import zhTW from "./zh-TW.json";

/**
 * i18n is reserved throughout the app: every player-facing string lives in a
 * resource bundle keyed by a stable id. zh-TW (繁體中文 / 台灣) is the default and
 * the only locale shipped today; adding a locale = adding one JSON bundle.
 */
export const DEFAULT_LOCALE = "zh-TW";

export const resources = {
  "zh-TW": { translation: zhTW },
} as const;

void i18n.use(initReactI18next).init({
  resources,
  lng: DEFAULT_LOCALE,
  fallbackLng: DEFAULT_LOCALE,
  defaultNS: "translation",
  interpolation: {
    escapeValue: false, // React already escapes
  },
  returnNull: false,
});

export default i18n;
