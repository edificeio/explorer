import { Suspense, lazy } from "react";

import { Plus } from "@edifice-ui/icons";
import {
  useOdeClient,
  Button,
  useToggle,
  LoadingScreen,
} from "@edifice-ui/react";
import { APP, IAction, odeServices } from "edifice-ts-client";
import { useTranslation } from "react-i18next";

import { useActions } from "~/services/queries";
import { useStoreActions } from "~/store";
import { searchContext } from "~/services/api";

const CreateModal = lazy(
  async () => await import("../../../Actionbar/components/EditResourceModal"),
);

export default function AppAction() {
  const [isCreateResourceOpen, toggle] = useToggle();

  const { appCode } = useOdeClient();
  const { t } = useTranslation(appCode);

  const { clearSelectedItems, clearSelectedIds } = useStoreActions();

  const { data: actions } = useActions();

  const canCreate = actions?.find((action: IAction) => action.id === "create");

  const handleOnResourceCreate = () => {
    if (appCode == APP.SCRAPBOOK) {
      odeServices.resource(appCode).gotoCreate();
      return;
    }
    clearSelectedItems();
    clearSelectedIds();
    toggle();
  };

  return canCreate ? (
    <>
      <Button
        type="button"
        color="primary"
        variant="filled"
        leftIcon={<Plus />}
        className="ms-auto"
        onClick={handleOnResourceCreate}
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
  ) : null;
}
