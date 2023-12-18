import { Suspense, lazy } from "react";

import { Plus } from "@edifice-ui/icons";
import {
  useOdeClient,
  Button,
  useToggle,
  LoadingScreen,
} from "@edifice-ui/react";
import { IAction } from "edifice-ts-client";
import { useTranslation } from "react-i18next";

import { useActions, useCreateResource } from "~/services/queries";
import { useCurrentFolder, useStoreActions } from "~/store";

const CreateModal = lazy(
  async () => await import("~/components/ResourceModal/ResourceModal"),
);

export default function AppAction() {
  const [isCreateResourceOpen, toggle] = useToggle();

  const { appCode } = useOdeClient();
  const { t } = useTranslation(appCode);

  const currentFolder = useCurrentFolder();
  const createResource = useCreateResource();

  const { clearSelectedItems, clearSelectedIds } = useStoreActions();
  const { data: actions } = useActions();

  const canCreate = actions?.find((action: IAction) => action.id === "create");

  const handleOnResourceCreate = () => {
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
            mode="create"
            currentFolder={currentFolder}
            createResource={createResource}
            isOpen={isCreateResourceOpen}
            onSuccess={toggle}
            onCancel={toggle}
          />
        )}
      </Suspense>
    </>
  ) : null;
}
