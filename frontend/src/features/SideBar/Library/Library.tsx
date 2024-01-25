import { ArrowRight } from "@edifice-ui/icons";
import {
  usePaths,
  useOdeTheme,
  useLibraryUrl,
  Image,
  Button,
} from "@edifice-ui/react";
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
      <a href={libraryUrl} target="_blank" rel="noreferrer">
        <Button
          rightIcon={<ArrowRight />}
          className="py-0 px-8"
          variant="ghost"
          color="primary"
        >
          {t("explorer.libray.btn")}
        </Button>
      </a>
    </div>
  );
};

export default Library;
