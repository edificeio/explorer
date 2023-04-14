import { forwardRef, type Ref } from "react";

import { Button, useOdeClient } from "@ode-react-ui/core";

const LoadMore = forwardRef((props, ref: Ref<HTMLButtonElement>) => {
  const { i18n } = useOdeClient();
  return (
    <div className="d-grid gap-2 col-4 mx-auto">
      <Button
        ref={ref}
        type="button"
        color="secondary"
        variant="filled"
        style={{ visibility: "hidden" }}
      >
        {i18n("explorer.see.more")}
      </Button>
    </div>
  );
});

if (import.meta.env.MODE === "dev") LoadMore.displayName = "LoadMore";

export default LoadMore;
