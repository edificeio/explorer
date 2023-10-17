import { useId, useState } from "react";

import { Alert, useHotToast, useOdeClient } from "@edifice-ui/react";
import { useQueryClient } from "@tanstack/react-query";
import { APP, type IResource } from "edifice-ts-client";
import { hash } from "ohash";
import { type SubmitHandler, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import slugify from "react-slugify";

import { useCreateResource, useUpdateResource } from "~/services/queries";
import {
  useCurrentFolder,
  useSearchParams,
  useSelectedResources,
} from "~/store";

interface useEditResourceModalProps {
  resource: IResource;
  edit: boolean;
  onSuccess: () => void;
  onCancel: () => void;
}

export interface FormInputs {
  title: string;
  description: string;
  enablePublic: boolean;
}

export default function useEditResourceModal({
  resource,
  edit,
  onSuccess,
  onCancel,
}: useEditResourceModalProps) {
  const { appCode } = useOdeClient();
  const { t } = useTranslation();
  const updateResource = useUpdateResource();
  const createResource = useCreateResource();
  const selectedResources = useSelectedResources();
  const searchParams = useSearchParams();
  const {
    watch,
    reset,
    register,
    handleSubmit,
    setFocus,
    setValue,
    formState: { errors, isSubmitting, isValid },
  } = useForm<FormInputs>({
    mode: "onChange",
  });

  const formId = useId();

  const currentFolder = useCurrentFolder();
  const { hotToast } = useHotToast(Alert);

  const [slug, setSlug] = useState<string>(resource?.slug || "");
  const [isPublic, setIsPublic] = useState<boolean>(!!resource?.public);
  const [thumbnail, setThumbnail] = useState<string | Blob | File>(
    selectedResources[0]?.thumbnail || "",
  );
  const [isLoading, setIsLoading] = useState(false);

  const resourceName = watch("title");

  const uniqueId = useId();

  const handleUploadImage = (file: File) => {
    setThumbnail(file);
  };

  const handleDeleteImage = () => {
    setThumbnail("");
  };

  function onPublicChange(value: boolean) {
    setIsPublic(value);

    let slug = "";

    if (resource && resource.slug) {
      slug = resource.slug;
    } else {
      slug = `${hash({
        foo: `${resourceName}${uniqueId}`,
      })}-${slugify(resourceName)}`;
    }

    setSlug(slug);
  }

  const queryclient = useQueryClient();

  const { filters, trashed } = searchParams;

  const queryKey = [
    "context",
    {
      folderId: filters.folder,
      filters,
      trashed,
    },
  ];

  const onSubmit: SubmitHandler<FormInputs> = async function (
    formData: FormInputs,
  ) {
    try {
      setIsLoading(true);

      const slug = formData.enablePublic
        ? resource && resource.slug
          ? resource.slug
          : `${hash({
              foo: `${formData.title}${uniqueId}`,
            })}-${slugify(formData.title)}`
        : "";
      // call API
      if (edit) {
        await updateResource.mutateAsync({
          description: formData.description || "",
          entId: selectedResources[0]?.assetId,
          name: formData.title,
          public: formData.enablePublic,
          slug,
          trashed: selectedResources[0]?.trashed,
          thumbnail,
        });
      } else {
        queryclient.invalidateQueries(queryKey);
        await createResource.mutateAsync({
          name: formData.title,
          description: formData.description || "",
          thumbnail,
          folder:
            currentFolder?.id === "default"
              ? undefined
              : parseInt(currentFolder?.id || ""),
          public: formData.enablePublic,
          slug,
          app: appCode,
        });
      }
      hotToast.success(
        <>
          <strong>
            {t(
              edit ? "explorer.resource.updated" : "explorer.resource.created",
            )}
          </strong>
          <p>Titre: {formData.title}</p>
          <p>Description: {formData.description}</p>
          {edit && appCode === APP.BLOG && (
            <p>
              Public:
              {formData.enablePublic
                ? t("explorer.enable.public.yes")
                : t("explorer.enable.public.no")}
            </p>
          )}
        </>,
      );
      onSuccess?.();
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  function onCopyToClipBoard() {
    navigator.clipboard.writeText(
      `${window.location.origin}${window.location.pathname}/pub/${slug}`,
    );
    hotToast.success(t("explorer.copy.clipboard"));
  }

  function onFormCancel() {
    reset();
    onCancel();
  }

  return {
    slug,
    isPublic,
    formId,
    errors,
    isSubmitting,
    isValid,
    resourceName,
    isLoading,
    onPublicChange,
    register,
    setFocus,
    setValue,
    handleSubmit,
    onFormCancel,
    onSubmit,
    handleUploadImage,
    handleDeleteImage,
    onCopyToClipBoard,
  };
}
