import { EmptyScreen } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { type IAction } from "ode-ts-client";
import { useTranslation } from "react-i18next";

import { useActions } from "~/services/queries";

export default function EmptyScreenApp(): JSX.Element {
  const { appCode, theme } = useOdeClient();
  const { t } = useTranslation();

  const { data: actions } = useActions();

  const canCreate = actions?.find((action: IAction) => action.id === "create");
  const labelEmptyScreenApp = () => {
    if (canCreate?.available && theme?.is1d) {
      // TODO should not have specific app i18n
      return t("explorer.emptyScreen.blog.txt1d.create");
    } else if (canCreate?.available && !theme?.is1d) {
      return t("explorer.emptyScreen.blog.txt2d.create");
    } else if (!canCreate?.available && theme?.is1d) {
      return t("explorer.emptyScreen.blog.txt1d.consultation");
    } else {
      return t("explorer.emptyScreen.blog.txt2d.consultation");
    }
  };

  return (
    <EmptyScreen
      imageSrc={`${theme?.bootstrapPath}/images/emptyscreen/illu-${appCode}.svg`}
      imageAlt={t("explorer.emptyScreen.app.alt")}
      title={`${
        canCreate?.available
          ? t("explorer.emptyScreen.blog.title.create")
          : t("explorer.emptyScreen.blog.title.consultation")
      }`}
      text={labelEmptyScreenApp()}
    />
  );
}
