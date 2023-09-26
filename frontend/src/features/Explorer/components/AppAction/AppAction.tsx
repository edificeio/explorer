import { Suspense, lazy } from "react";

import { Plus } from "@edifice-ui/icons";
import {
  useOdeClient,
  Button,
  useToggle,
  LoadingScreen,
} from "@edifice-ui/react";
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
