import { ArrowRight } from "@edifice-ui/icons";
import { usePaths, useOdeTheme, useLibraryUrl, Image } from "@edifice-ui/react";
import { useTranslation } from "react-i18next";

const Library = () => {
  const { t } = useTranslation();
  const { theme } = useOdeTheme();
  const { libraryUrl } = useLibraryUrl();
  const [imagePath] = usePaths();

  return (
    <div className="p-16">
      <Image
        width="270"
        height="140"
        loading="lazy"
        className="rounded"
        src={`${imagePath}/${theme?.bootstrapVersion}/image-library.svg`}
        alt={t("explorer.libray.img.alt")}
      />
      <p className="m-8">{t("explorer.libray.title")}</p>
      <a
        href={libraryUrl}
        target="_blank"
        rel="noreferrer"
        className="d-inline-flex gap-4 btn btn-ghost-primary py-0 p-0 pe-8"
      >
        <ArrowRight />
        <span>{t("explorer.libray.btn")}</span>
      </a>
    </div>
  );
};

export default Library;
