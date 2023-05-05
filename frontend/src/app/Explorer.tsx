import { Suspense, lazy } from "react";

import ActionBarContainer from "@features/Actionbar/components/ActionBarContainer";
import { AppHeader } from "@features/Explorer/components";
import { List } from "@features/Explorer/components/List/List";
import { TreeViewContainer } from "@features/TreeView/components/TreeViewContainer";
import {
  AppCard,
  Grid,
  useOdeClient,
  AppIcon,
  LoadingScreen,
} from "@ode-react-ui/core";
import { useActions } from "@services/queries";
import { Breadcrumb } from "@shared/components/Breadcrumb";
import { useOnboardingModal } from "@shared/hooks/useOnboardingModal";
import { type IAction } from "ode-ts-client";

const OnboardingModal = lazy(
  async () => await import("@shared/components/OnboardingModal"),
);

const AppAction = lazy(
  async () => await import("@features/Explorer/components/AppAction/AppAction"),
);

const Library = lazy(
  async () => await import("@features/Explorer/components/Library/Library"),
);

export default function Explorer(): JSX.Element | null {
  const { app } = useOdeClient();
  const { isOnboardingTrash, isOpen, setIsOpen } = useOnboardingModal();
  const { data: actions } = useActions();

  const canPublish = actions?.find(
    (action: IAction) => action.id === "publish",
  );

  function isActionAvailable(value: string) {
    const found = actions?.filter(
      (action: IAction) => action.id === value && action.available,
    );
    return found && found.length > 0;
  }

  return (
    <>
      <AppHeader>
        <AppCard app={app} isHeading headingStyle="h3" level="h1">
          <AppIcon app={app} size="40" />
          <AppCard.Name />
        </AppCard>
        {isActionAvailable("create") && (
          <Suspense fallback={<LoadingScreen />}>
            <AppAction />
          </Suspense>
        )}
      </AppHeader>
      <Grid>
        <Grid.Col
          sm="3"
          className="border-end pt-16 pe-16 d-none d-lg-block"
          as="aside"
        >
          <TreeViewContainer />
          {canPublish?.available && (
            <Suspense fallback={<LoadingScreen />}>
              <Library />
            </Suspense>
          )}
        </Grid.Col>
        <Grid.Col sm="4" md="8" lg="9">
          <Breadcrumb />
          <List />
        </Grid.Col>
        <ActionBarContainer />
        <Suspense fallback={<LoadingScreen />}>
          {isOnboardingTrash && (
            <OnboardingModal isOpen={isOpen} setIsOpen={setIsOpen} />
          )}
        </Suspense>
      </Grid>
    </>
  );
}
