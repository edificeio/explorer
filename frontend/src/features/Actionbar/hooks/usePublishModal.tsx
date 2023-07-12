import { useState } from "react";

import { Alert } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { useHotToast } from "@ode-react-ui/hooks";
import {
  RESOURCE,
  type PublishParameters,
  type PublishResult,
} from "ode-ts-client";
import { type SubmitHandler, useForm } from "react-hook-form";

import {
  PublishModalSuccess,
  PublishModalError,
} from "../components/PublishModal";
import { http } from "~/shared/constants";
import { capitalizeFirstLetter } from "~/shared/utils/capitalizeFirstLetter";
import { useStoreActions, useResourceIds, useSelectedResources } from "~/store";

interface ModalProps {
  onSuccess?: () => void;
}

export interface InputProps {
  title: string;
  description: string;
  activityType: string;
  subjectArea: string;
  language: string;
  ageMin: string;
  ageMax: string;
  keyWords: string;
}

export default function usePublishModal({ onSuccess }: ModalProps) {
  const { user, currentApp } = useOdeClient();

  const selectedResources = useSelectedResources();

  const [cover, setCover] = useState<Record<string, string>>({
    name: "",
    image: selectedResources[0].thumbnail,
  });

  const [loaderPublish, setLoaderPublish] = useState<boolean>(false);

  const { hotToast } = useHotToast(Alert);

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const resourceIds = useResourceIds();
  const { publishApi } = useStoreActions();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty, isValid },
  } = useForm<InputProps>({ mode: "onChange" });

  // models for Dropdown select lists
  const [selectedActivities, setSelectedActivities] = useState<
    Array<string | number>
  >([]);
  const [selectedSubjectAreas, setSelectedSubjectAreas] = useState<
    Array<string | number>
  >([]);

  function handleUploadImage(preview: Record<string, string>) {
    setCover(preview);
  }

  const userId = user ? user?.userId : "";

  const handleDeleteImage = () => {
    setCover({
      name: "",
      image: "",
    });
  };

  const publish: SubmitHandler<InputProps> = async (formData: InputProps) => {
    try {
      setLoaderPublish(true);
      let coverBlob = new Blob();
      if (cover.image) {
        coverBlob = await http.get(cover.image, { responseType: "blob" });
      } else if (selectedResources[0].thumbnail) {
        coverBlob = await http.get(selectedResources[0].thumbnail, {
          responseType: "blob",
        });
      }

      const teacherAvatar: Blob = await http.get(
        `/userbook/avatar/${userId}?thumbnail=48x48`,
        { responseType: "blob" } as any,
      );

      const resAttachmentSchool = await http.get(
        `/directory/user/${userId}/attachment-school`,
        { responseType: "json" } as any,
      );

      let appName = "";
      if (currentApp?.displayName) {
        appName = capitalizeFirstLetter(currentApp?.displayName);
      }

      const parameters: PublishParameters = {
        activityType: selectedActivities as string[],
        age: [formData.ageMin, formData.ageMax],
        application: capitalizeFirstLetter(appName),
        cover: coverBlob,
        description: formData.description,
        keyWords: formData.keyWords,
        language: formData.language,
        licence: "CC-BY",
        resourceId: selectedResources[0].assetId,
        resourceEntId: selectedResources[0].assetId,
        subjectArea: selectedSubjectAreas as string[],
        teacherAvatar,
        title: formData.title,
        userId,
        userStructureName: resAttachmentSchool.name || user?.structureNames[0],
      };

      const resourceType = [RESOURCE.BLOG][0];
      const result: PublishResult = (await publishApi(
        resourceType,
        parameters,
      )) as PublishResult;

      if (result.success) {
        hotToast.success(<PublishModalSuccess result={result} />, {
          duration: 10000,
        });
      } else {
        hotToast.error(<PublishModalError formData={formData} />);
      }
      onSuccess?.();
    } catch (e) {
      hotToast.error(<PublishModalError formData={formData} />);
    } finally {
      setLoaderPublish(false);
    }
  };

  return {
    selectedResources,
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty, isValid },
    publish,
    handleDeleteImage,
    handleUploadImage,
    selectedActivities,
    setSelectedActivities,
    selectedSubjectAreas,
    setSelectedSubjectAreas,
    loaderPublish,
    cover,
  };
}
