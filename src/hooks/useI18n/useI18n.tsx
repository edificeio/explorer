import { useIdiom } from "@store/useOdeStore";

/** Custom Hook for ode-ts-client integration */
export default function useI18n() {
  const idiom = useIdiom();
  return {
    i18n: idiom.translate,
  };
}
