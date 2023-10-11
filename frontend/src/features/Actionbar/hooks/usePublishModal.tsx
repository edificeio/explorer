import { useState } from "react";

import { Alert, useOdeClient, useHotToast } from "@edifice-ui/react";
import {
  RESOURCE,
  type PublishParameters,
  type PublishResult,
} from "edifice-ts-client";
import { type SubmitHandler, useForm } from "react-hook-form";

import {
  PublishModalSuccess,
  PublishModalError,
} from "../components/PublishModal";
import { http } from "~/constants";
import { useStoreActions, useSelectedResources } from "~/store";
import { capitalizeFirstLetter } from "~/utils/capitalizeFirstLetter";
import { getAppParams } from "~/utils/getAppParams";

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
  const { publishApi } = useStoreActions();

  const {
    register,
    watch,
    setValue,
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

  const selectActivities = (value: string | number) => {
    let checked = [...selectedActivities];
    const findIndex = checked.findIndex(
      (item: string | number): boolean => item === value,
    );

    if (!selectedActivities.includes(value)) {
      checked = [...selectedActivities, value];
    } else {
      checked = selectedActivities.filter(
        (_, index: number) => index !== findIndex,
      );
    }

    setSelectedActivities(checked);
  };

  const selectSubjects = (value: string | number) => {
    let checked = [...selectedSubjectAreas];
    const findIndex = checked.findIndex(
      (item: string | number): boolean => item === value,
    );

    if (!selectedSubjectAreas.includes(value)) {
      checked = [...selectedSubjectAreas, value];
    } else {
      checked = selectedSubjectAreas.filter(
        (_, index: number) => index !== findIndex,
      );
    }

    setSelectedSubjectAreas(checked);
  };

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
        application:
          getAppParams().libraryAppFilter ?? capitalizeFirstLetter(appName),
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
    watch,
    setValue,
    selectedResources,
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty, isValid },
    publish,
    handleDeleteImage,
    handleUploadImage,
    selectedActivities,
    selectActivities,
    selectedSubjectAreas,
    selectSubjects,
    loaderPublish,
    cover,
  };
}
