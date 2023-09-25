import { EmptyScreen, useOdeClient, usePaths } from "@edifice-ui/react";
import { useTranslation } from "react-i18next";

export default function EmptyScreenError(): JSX.Element {
  const { appCode } = useOdeClient();
  const [imagePath] = usePaths();
  const { t } = useTranslation();

  return (
    <EmptyScreen
      imageSrc={`${imagePath}/emptyscreen/illu-error.svg`}
      imageAlt={t("explorer.emptyScreen.error.alt", { ns: appCode })}
      title=""
      text={
        "Un problème est survenu lors du chargement des ressources. Si le problème persiste, contactez le support."
      }
    />
  );
}
