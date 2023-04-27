import ActionBarContainer from "@features/Actionbar/components/ActionBarContainer";
import { AppHeader } from "@features/Explorer/components";
import { AppAction } from "@features/Explorer/components/AppAction/AppAction";
import { List } from "@features/Explorer/components/List/List";
import { TreeViewContainer } from "@features/TreeView/components/TreeViewContainer";
import {
  AppCard,
  Grid,
  useOdeClient,
  AppIcon,
  Library,
} from "@ode-react-ui/core";
import { useActions } from "@services/queries";
import { Breadcrumb } from "@shared/components/Breadcrumb";
import OnBoardingTrash from "@shared/components/OnBoardingModal";
import { capitalizeFirstLetter } from "@shared/utils/capitalizeFirstLetter";
import { type IAction } from "ode-ts-client";

export default function Explorer(): JSX.Element | null {
  const { i18n, app, appCode, getBootstrapTheme } = useOdeClient();
  const { data: actions } = useActions();

  const canPublish = actions?.find(
    (action: IAction) => action.id === "publish",
  );
  const LIB_URL = `https://library.opendigitaleducation.com/search/?application%5B0%5D=${capitalizeFirstLetter(
    appCode,
  )}&page=1&sort_field=views&sort_order=desc`;

  return (
    <>
      <AppHeader>
        <AppCard app={app} isHeading headingStyle="h3" level="h1">
          <AppIcon app={app} size="40" />
          <AppCard.Name />
        </AppCard>
        <AppAction />
      </AppHeader>
      <Grid>
        <Grid.Col
          sm="3"
          className="border-end pt-16 pe-16 d-none d-lg-block"
          as="aside"
        >
          <TreeViewContainer />
          {canPublish?.available && (
            <Library
              src={`${getBootstrapTheme()}/images/image-library.svg`}
              alt={i18n("explorer.libray.img.alt")}
              text={i18n("explorer.libray.title")}
              url={LIB_URL}
              textButton={i18n("explorer.libray.btn")}
            />
          )}
        </Grid.Col>
        <Grid.Col sm="4" md="8" lg="9">
          <Breadcrumb />
          <List />
        </Grid.Col>
        <ActionBarContainer />
        <OnBoardingTrash />
      </Grid>
    </>
  );
}
