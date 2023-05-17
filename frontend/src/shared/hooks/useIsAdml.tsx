import { useCallback, useEffect, useState } from "react";

import { odeServices } from "ode-ts-client";

export const useIsAdml = () => {
  const [isAdml, setIsAdml] = useState(false);

  useEffect(() => {
    initIsAdml();
  }, []);

  const initIsAdml = useCallback(async () => {
    const isAdmlRes = await odeServices.session().isAdml();
    setIsAdml(isAdmlRes);
  }, []);

  return {
    isAdml,
  };
};
