import {
  Heading,
  Alert,
  FormControl,
  Input,
  FormText,
  Button,
} from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { Copy } from "@ode-react-ui/icons";

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
  const { i18n } = useOdeClient();

  return (
    <>
      <Heading headingStyle="h4" level="h3" className="mb-16">
        {i18n("explorer.resource.editModal.heading.access")}
        {appCode}
      </Heading>

      <Alert type="info">
        {i18n("explorer.resource.editModal.access.alert")}
      </Alert>

      <FormControl
        id="flexSwitchCheckDefault"
        className="form-switch d-flex gap-8 mt-16 mb-8"
      >
        <FormControl.Input
          type="checkbox"
          role="switch"
          key={refPublic}
          {...register("enablePublic", {
            value: resource.public!,
            onChange: (e: { target: { checked: any } }) =>
              onPublicChange(e.target.checked),
          })}
          className="form-check-input mt-0"
          size="md"
        />
        <FormControl.Label className="form-check-label mb-0">
          {i18n(
            "explorer.resource.editModal.access.flexSwitchCheckDefault.label",
          )}
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
                      return i18n("explorer.slug.name.mandatory");
                    return true;
                  },
                },
                disabled: disableSlug,
                value: slug,
                onChange: (e: { target: { value: any } }) =>
                  onSlugChange(e.target.value),
              })}
              size="md"
              placeholder={i18n(
                "explorer.resource.editModal.access.url.extension",
              )}
            />
            {correctSlug && (
              <div className="position-absolute">
                <FormText>{i18n("explorer.slug.name.error")}</FormText>
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
            {i18n("explorer.resource.editModal.access.url.button")}
          </Button>
        </div>
      </FormControl>
    </>
  );
};
