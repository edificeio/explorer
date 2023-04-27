import { useOdeClient, EmptyScreen } from "@ode-react-ui/core";
import { useActions } from "@services/queries";
import { imageBootstrap } from "@shared/constants";
import { type IAction } from "ode-ts-client";

export function EmptyScreenApp(): JSX.Element {
  const { i18n, appCode, is1d } = useOdeClient();

  const { data: actions } = useActions();

  const canCreate = actions?.find((action: IAction) => action.id === "create");
  const labelEmptyScreenApp = () => {
    if (canCreate?.available && is1d) {
      // TODO should not have specific app i18n
      return i18n("explorer.emptyScreen.blog.txt1d.create");
    } else if (canCreate?.available && !is1d) {
      return i18n("explorer.emptyScreen.blog.txt2d.create");
    } else if (!canCreate?.available && is1d) {
      return i18n("explorer.emptyScreen.blog.txt1d.consultation");
    } else {
      return i18n("explorer.emptyScreen.blog.txt2d.consultation");
    }
  };

  return (
    <EmptyScreen
      imageSrc={`${imageBootstrap}/emptyscreen/illu-${appCode}.svg`}
      imageAlt={i18n("explorer.emptyScreen.app.alt")}
      title={`${
        canCreate?.available
          ? i18n("explorer.emptyScreen.blog.title.create")
          : i18n("explorer.emptyScreen.blog.title.consultation")
      }`}
      text={labelEmptyScreenApp()}
    />
  );
}
