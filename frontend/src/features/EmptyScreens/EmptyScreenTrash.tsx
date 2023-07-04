import { EmptyScreen } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { useTranslation } from "react-i18next";

export default function EmptyScreenTrash(): JSX.Element {
  const { appCode } = useOdeClient();
  const { theme } = useOdeClient();
  const { t } = useTranslation();

  return (
    <EmptyScreen
      imageSrc={`${theme?.bootstrapPath}/images/emptyscreen/illu-trash.svg`}
      imageAlt={t("explorer.emptyScreen.trash.alt")}
      title={t("explorer.emptyScreen.trash.title")}
      text={t("explorer.emptyScreen.trash.empty", { ns: appCode })}
    />
  );
}
