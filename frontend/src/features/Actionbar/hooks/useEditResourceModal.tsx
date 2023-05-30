import { useId, useState } from "react";

import { Alert } from "@ode-react-ui/components";
import { useI18n } from "@ode-react-ui/core";
import { useHotToast } from "@ode-react-ui/hooks";
import { type IResource } from "ode-ts-client";
import { type SubmitHandler, useForm } from "react-hook-form";

import { useUpdateResource } from "~/services/queries";
import { useSelectedResources } from "~/store";

interface useEditResourceModalProps {
  resource: IResource;
  onSuccess: () => void;
  onCancel: () => void;
}

interface FormInputs {
  title: string;
  description: string;
  enablePublic: boolean;
  safeSlug: string;
}

export default function useEditResourceModal({
  resource,
  onSuccess,
  onCancel,
}: useEditResourceModalProps) {
  const { i18n } = useI18n();
  const [cover, setCover] = useState<{ name: string; image: string }>({
    name: "",
    image: "",
  });
  const [versionSlug, setVersionSlug] = useState<number>(new Date().getTime());
  const [disableSlug, setDisableSlug] = useState<boolean>(!resource.public);
  const [slug, setSlug] = useState<string>(resource.slug || "");
  const [correctSlug, setCorrectSlug] = useState<boolean>(false);

  const { hotToast } = useHotToast(Alert);
  const updateResource = useUpdateResource();
  const selectedResources = useSelectedResources();
  const {
    reset,
    register,
    handleSubmit,
    setFocus,
    formState: { errors, isSubmitting, isValid },
  } = useForm<FormInputs>({
    mode: "onChange",
  });

  const id = useId();

  const handleUploadImage = (preview: Record<string, string>) => {
    setCover(preview as any);
  };

  function onPublicChange(pub: boolean) {
    setDisableSlug(!pub);
    if (!pub) {
      setSlug("");
      setVersionSlug(new Date().getTime());
    }
  }

  function onSlugChange(slug: string) {
    if (!slug) return "";
    const a = "àáäâãåăæçèéëêǵḧìíïîḿńǹñòóöôœøṕŕßśșțùúüûǘẃẍÿź·/_,:;";
    const b = "aaaaaaaaceeeeghiiiimnnnooooooprssstuuuuuwxyz------";
    const p = new RegExp(a.split("").join("|"), "g");
    const res = slug
      .toString()
      .toLowerCase()
      .replace(/\s+/g, "-") // Replace spaces with -
      .replace(p, (c) => b.charAt(a.indexOf(c))) // Replace special characters
      .replace(/&/g, "-and-") // Replace & with ‘and’
      .replace(/[^\w\\-]+/g, "") // Remove all non-word characters
      .replace(/\\-\\-+/g, "-") // Replace multiple - with single -
      .replace(/^-+/, "") // Trim - from start of text
      .replace(/-+$/, ""); // Trim - from end of text
    setSlug(res);
  }

  const onSubmit: SubmitHandler<FormInputs> = async function (
    formData: FormInputs,
  ) {
    try {
      // call API
      await updateResource.mutateAsync({
        description: formData.description,
        entId: selectedResources[0].assetId,
        name: formData.title,
        public: formData.enablePublic,
        slug: formData.safeSlug,
        trashed: selectedResources[0].trashed,
        thumbnail: cover.image || selectedResources[0].thumbnail,
      });
      setCorrectSlug(false);
      hotToast.success(
        <>
          <strong>{i18n("explorer.resource.updated")}</strong>
          <p>Titre: {formData.title}</p>
          <p>Description: {formData.description}</p>
          <p>Public: {formData.enablePublic ? "Oui" : "Non"}</p>
        </>,
      );
      onSuccess?.();
    } catch (e) {
      setCorrectSlug(true);
      console.error(e);
      // hotToast.error(`Error: ${error}`);
    }
  };

  function onCopyToClipBoard(_: string) {
    navigator.clipboard.writeText(`${window.location.origin}/${slug}`);
    hotToast.success("L'adresse a été copié dans le presse papier");
  }

  function onFormCancel() {
    reset();
    onCancel();
  }

  const formId = `resource_edit_modal_${id}`;

  return {
    slug,
    disableSlug,
    formId,
    errors,
    isSubmitting,
    isValid,
    versionSlug,
    correctSlug,
    onPublicChange,
    onSlugChange,
    register,
    setFocus,
    handleSubmit,
    onFormCancel,
    onSubmit,
    handleUploadImage,
    onCopyToClipBoard,
  };
}
