import { useOdeContext } from "@contexts/Ode/OdeContext";

/** Custom Hook for ode-ts-client integration */
export default function useI18n() {
  const { idiom } = useOdeContext();
  return {
    i18n: idiom.translate,
  };
}
