import { EmptyScreen } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { useTranslation } from "react-i18next";

export default function EmptyScreenNoContentInFolder(): JSX.Element | null {
  const { theme } = useOdeClient();
  const { t } = useTranslation();

  return (
    <EmptyScreen
      imageSrc={`${theme?.bootstrapPath}/images/emptyscreen/illu-no-content-in-folder.svg`}
      imageAlt={t("explorer.emptyScreen.folder.empty.alt")}
      text={t("explorer.emptyScreen.folder.title")}
    />
  );
}
