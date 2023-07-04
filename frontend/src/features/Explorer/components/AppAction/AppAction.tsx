import { Button } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";
import { useTranslation } from "react-i18next";

import { useStoreActions } from "~/store";

export default function AppAction() {
  const { appCode } = useOdeClient();
  const { t } = useTranslation(appCode);
  const { createResource } = useStoreActions();

  return (
    <Button
      type="button"
      color="primary"
      variant="filled"
      leftIcon={<Plus />}
      className="ms-auto"
      onClick={createResource}
    >
      {t("explorer.create.title")}
    </Button>
  );
}
