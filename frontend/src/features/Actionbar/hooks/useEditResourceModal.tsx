import { useId, useState } from "react";

import useExplorerStore from "@store/index";
import { type IResource } from "ode-ts-client";
import { type SubmitHandler, useForm } from "react-hook-form";
import { toast } from "react-hot-toast";

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
  const [cover, setCover] = useState<{ name: string; image: string }>({
    name: "",
    image: "",
  });
  const [versionSlug, setVersionSlug] = useState<number>(new Date().getTime());
  const [disableSlug, setDisableSlug] = useState<boolean>(!resource.public);
  const [slug, setSlug] = useState<string>(resource.slug || "");
  const updateResource = useExplorerStore((state) => state.updateResource);
  const selectedResources = useExplorerStore((state) =>
    state.getSelectedIResources(),
  );
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
      updateResource({
        description: formData.description,
        entId: selectedResources[0].assetId,
        name: formData.title,
        public: formData.enablePublic,
        slug: formData.safeSlug,
        trashed: selectedResources[0].trashed,
        thumbnail: cover.image || selectedResources[0].thumbnail,
      });
      toast.success(
        <>
          <h3>Coming Soon!</h3>
          <p>Titre: {formData.title}</p>
          <p>Description: {formData.description}</p>
          <p>Public: {formData.enablePublic}</p>
        </>,
      );
      onSuccess?.();
    } catch (error) {
      console.error(error);
      toast.error(`Error: ${error}`);
    }
  };

  function onCopyToClipBoard(_: string) {
    navigator.clipboard.writeText(`${window.location.origin}/${slug}`);
    toast.success(<>{"L'adresse a été copié dans le presse papier"}</>);
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
