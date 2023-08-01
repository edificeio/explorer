import { Suspense, lazy } from "react";

import { Button, LoadingScreen } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { useToggle } from "@ode-react-ui/hooks";
import { Plus } from "@ode-react-ui/icons";
import { useTranslation } from "react-i18next";

const CreateModal = lazy(
  async () => await import("../../../Actionbar/components/EditResourceModal"),
);

export default function AppAction() {
  const [isCreateResourceOpen, toggle] = useToggle();
  const { appCode } = useOdeClient();
  const { t } = useTranslation(appCode);

  return (
    <>
      <Button
        type="button"
        color="primary"
        variant="filled"
        leftIcon={<Plus />}
        className="ms-auto"
        onClick={toggle}
      >
        {t("explorer.create.title")}
      </Button>

      <Suspense fallback={<LoadingScreen />}>
        {isCreateResourceOpen && (
          <CreateModal
            edit={false}
            isOpen={isCreateResourceOpen}
            onSuccess={toggle}
            onCancel={toggle}
          />
        )}
      </Suspense>
    </>
  );
}
