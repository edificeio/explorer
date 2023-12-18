import { useState } from "react";

import { Alert, useOdeClient, useHotToast } from "@edifice-ui/react";
import {
  IResource,
  odeServices,
  type PublishParameters,
} from "edifice-ts-client";
import { type SubmitHandler, useForm } from "react-hook-form";

import { ToastError } from "../components/ToastError";
import { ToastSuccess } from "../components/ToastSuccess";
import { libraryMaps } from "~/constants";

interface ModalProps {
  resource: IResource;
  onSuccess?: () => void;
}

export interface FormDataProps {
  title: string;
  description: string;
  activityType: string;
  subjectArea: string;
  language: string;
  ageMin: string;
  ageMax: string;
  keyWords: string;
}

export default function usePublishModal({ onSuccess, resource }: ModalProps) {
  const { user, appCode } = useOdeClient();
  const { hotToast } = useHotToast(Alert);

  const [cover, setCover] = useState<string | Blob | File>(
    resource.thumbnail || "",
  );

  const {
    control,
    register,
    watch,
    setValue,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty, isValid },
  } = useForm<FormDataProps>({ mode: "onChange" });

  // models for Dropdown select lists
  const [selectedActivities, setSelectedActivities] = useState<
    Array<string | number>
  >([]);

  const [selectedSubjectAreas, setSelectedSubjectAreas] = useState<
    Array<string | number>
  >([]);

  const selectOption = (
    value: string | number,
    selectedOptions: Array<string | number>,
    setSelectedOptions: React.Dispatch<
      React.SetStateAction<Array<string | number>>
    >,
  ) => {
    let checked = [...selectedOptions];
    const findIndex = checked.findIndex(
      (item: string | number): boolean => item === value,
    );

    if (!selectedOptions.includes(value)) {
      checked = [...selectedOptions, value];
    } else {
      checked = selectedOptions.filter(
        (_, index: number) => index !== findIndex,
      );
    }

    setSelectedOptions(checked);
  };

  const selectActivities = (value: string | number) => {
    selectOption(value, selectedActivities, setSelectedActivities);
  };

  const selectSubjects = (value: string | number) => {
    selectOption(value, selectedSubjectAreas, setSelectedSubjectAreas);
  };

  const handleUploadImage = (file: File) => setCover(file);

  const handleDeleteImage = () => setCover("");

  const handlePublish: SubmitHandler<FormDataProps> = async (
    formData: FormDataProps,
  ) => {
    const userId = user ? user?.userId : "";

    try {
      let coverBlob = new Blob();
      if (typeof cover === "string") {
        coverBlob = await odeServices.http().get(cover, {
          responseType: "blob",
        });
      } else if (cover) {
        coverBlob = await odeServices
          .http()
          .get(URL.createObjectURL(cover as Blob), {
            responseType: "blob",
          });
      } else if (resource.thumbnail) {
        coverBlob = await odeServices.http().get(resource.thumbnail, {
          responseType: "blob",
        });
      }

      const teacherAvatar: Blob = await odeServices
        .http()
        .get(`/userbook/avatar/${userId}?thumbnail=48x48`, {
          responseType: "blob",
        } as any);

      const resAttachmentSchool = await odeServices
        .http()
        .get(`/directory/user/${userId}/attachment-school`, {
          responseType: "json",
        } as any);

      const application = libraryMaps[appCode as string];

      const parameters: PublishParameters = {
        activityType: selectedActivities as string[],
        age: [formData.ageMin, formData.ageMax],
        application,
        cover: coverBlob,
        description: formData.description,
        keyWords: formData.keyWords,
        language: formData.language,
        licence: "CC-BY",
        resourceId: resource.assetId,
        resourceEntId: resource.assetId,
        subjectArea: selectedSubjectAreas as string[],
        teacherAvatar,
        title: formData.title,
        userId,
        userStructureName: resAttachmentSchool.name || user?.structureNames[0],
      };

      const result = await odeServices
        .resource(application)
        .publish(parameters);

      if (result.success) {
        hotToast.success(<ToastSuccess result={result} />, {
          duration: 10000,
        });
      } else {
        hotToast.error(<ToastError formData={formData} />);
      }
      onSuccess?.();
    } catch (error) {
      console.error(error);
      hotToast.error(<ToastError formData={formData} />);
    }
  };

  return {
    control,
    cover,
    formState: { errors, isSubmitting, isDirty, isValid },
    handleDeleteImage,
    handlePublish,
    handleSubmit,
    handleUploadImage,
    register,
    selectActivities,
    selectedActivities,
    selectedSubjectAreas,
    selectSubjects,
    setValue,
    watch,
  };
}
