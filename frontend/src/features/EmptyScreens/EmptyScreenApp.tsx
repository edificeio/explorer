import { EmptyScreen } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { type IAction } from "ode-ts-client";

import { useActions } from "~services/queries";

export default function EmptyScreenApp(): JSX.Element {
  const { i18n, appCode, theme } = useOdeClient();

  const { data: actions } = useActions();

  const canCreate = actions?.find((action: IAction) => action.id === "create");
  const labelEmptyScreenApp = () => {
    if (canCreate?.available && theme?.is1d) {
      // TODO should not have specific app i18n
      return i18n("explorer.emptyScreen.blog.txt1d.create");
    } else if (canCreate?.available && !theme?.is1d) {
      return i18n("explorer.emptyScreen.blog.txt2d.create");
    } else if (!canCreate?.available && theme?.is1d) {
      return i18n("explorer.emptyScreen.blog.txt1d.consultation");
    } else {
      return i18n("explorer.emptyScreen.blog.txt2d.consultation");
    }
  };

  return (
    <EmptyScreen
      imageSrc={`${theme?.bootstrapPath}/images/emptyscreen/illu-${appCode}.svg`}
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
