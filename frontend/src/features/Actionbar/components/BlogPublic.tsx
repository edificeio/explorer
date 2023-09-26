import { Copy } from "@edifice-ui/icons";
import {
  Heading,
  Alert,
  FormControl,
  Input,
  FormText,
  Button,
} from "@edifice-ui/react";
import { useTranslation } from "react-i18next";

export const BlogPublic = ({
  appCode,
  correctSlug,
  disableSlug,
  onPublicChange,
  onSlugChange,
  refPublic,
  refSlug,
  register,
  resource,
  slug,
  onCopyToClipBoard,
}: any) => {
  const { t } = useTranslation();

  return (
    <>
      <Heading headingStyle="h4" level="h3" className="mb-16">
        {t("explorer.resource.editModal.heading.access")}
        {appCode}
      </Heading>

      <Alert type="info">{t("explorer.resource.editModal.access.alert")}</Alert>

      <FormControl
        id="flexSwitchCheckDefault"
        className="form-switch d-flex gap-8 mt-16 mb-8"
      >
        <FormControl.Input
          type="checkbox"
          role="switch"
          key={refPublic}
          {...register("enablePublic", {
            value: resource && resource.public,
            onChange: (e: { target: { checked: any } }) =>
              onPublicChange(e.target.checked),
          })}
          className="form-check-input mt-0"
          size="md"
        />
        <FormControl.Label className="form-check-label mb-0">
          {t("explorer.resource.editModal.access.flexSwitchCheckDefault.label")}
        </FormControl.Label>
      </FormControl>

      <FormControl id="slug" status={correctSlug ? "invalid" : undefined}>
        <div className="d-flex flex-wrap align-items-center gap-4">
          <div>
            {window.location.origin}
            {window.location.pathname}/pub/
          </div>

          <div className="flex-fill">
            <Input
              type="text"
              key={refSlug}
              {...register("safeSlug", {
                validate: {
                  required: (value: any) => {
                    if (!value && !disableSlug)
                      return t("explorer.slug.name.mandatory");
                    return true;
                  },
                },
                disabled: disableSlug,
                value: slug,
                onChange: (e: { target: { value: any } }) =>
                  onSlugChange(e.target.value),
              })}
              size="md"
              placeholder={t(
                "explorer.resource.editModal.access.url.extension",
              )}
            />
            {correctSlug && (
              <div className="position-absolute">
                <FormText>{t("explorer.slug.name.error")}</FormText>
              </div>
            )}
          </div>
          <Button
            color="primary"
            disabled={disableSlug}
            onClick={() => {
              onCopyToClipBoard();
            }}
            type="button"
            leftIcon={<Copy />}
            variant="ghost"
            className="text-nowrap"
          >
            {t("explorer.resource.editModal.access.url.button")}
          </Button>
        </div>
      </FormControl>
    </>
  );
};
