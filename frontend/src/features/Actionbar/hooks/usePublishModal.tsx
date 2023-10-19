import { useState } from "react";

import { Alert, useOdeClient, useHotToast } from "@edifice-ui/react";
import { type PublishParameters, type PublishResult } from "edifice-ts-client";
import { type SubmitHandler, useForm } from "react-hook-form";

import {
  PublishModalSuccess,
  PublishModalError,
} from "../components/PublishModal";
import { http, libraryMaps } from "~/constants";
import {
  useStoreActions,
  useSelectedResources,
  useSearchParams,
} from "~/store";
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
  const { user, appCode } = useOdeClient();

  const userId = user ? user?.userId : "";

  const selectedResources = useSelectedResources();
  const searchParams = useSearchParams();

  const [cover, setCover] = useState<string | Blob | File>(
    selectedResources[0]?.thumbnail || "",
  );

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

  const handleUploadImage = (file: File) => {
    setCover(file);
  };

  const handleDeleteImage = () => {
    setCover("");
  };

  const publish: SubmitHandler<InputProps> = async (formData: InputProps) => {
    try {
      setLoaderPublish(true);
      let coverBlob = new Blob();
      if (typeof cover === "string") {
        coverBlob = await http.get(cover, {
          responseType: "blob",
        });
      } else if (cover) {
        coverBlob = await http.get(URL.createObjectURL(cover as Blob), {
          responseType: "blob",
        });
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

      const appName = libraryMaps[appCode as string];

      const parameters: PublishParameters = {
        activityType: selectedActivities as string[],
        age: [formData.ageMin, formData.ageMax],
        application: getAppParams().libraryAppFilter ?? appName,
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

      const resourceType = searchParams.types[0];
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
