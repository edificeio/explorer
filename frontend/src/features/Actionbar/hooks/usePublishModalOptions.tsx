import { type OptionsType } from "@ode-react-ui/components";
import { useTranslation } from "react-i18next";

export default function usePublishLibraryModalOptions() {
  const { t } = useTranslation();

  // static values and labels to display in Dropdown select lists
  const activityTypeOptions: OptionsType[] = [
    {
      value: "bpr.activityType.classroomActivity",
      label: t("bpr.activityType.classroomActivity"),
    },
    {
      value: "bpr.activityType.groupActivity",
      label: t("bpr.activityType.groupActivity"),
    },
    {
      value: "bpr.activityType.personalActivity",
      label: t("bpr.activityType.personalActivity"),
    },
    {
      value: "bpr.activityType.homework",
      label: t("bpr.activityType.homework"),
    },
    {
      value: "bpr.activityType.exercize",
      label: t("bpr.activityType.exercize"),
    },
    {
      value: "bpr.activityType.learningPath",
      label: t("bpr.activityType.learningPath"),
    },
    {
      value: "bpr.activityType.courseElement",
      label: t("bpr.activityType.courseElement"),
    },
    {
      value: "bpr.other",
      label: t("bpr.other"),
    },
  ];

  const subjectAreaOptions: OptionsType[] = [
    {
      value: "bpr.subjectArea.artActivity",
      label: t("bpr.subjectArea.artActivity"),
    },
    {
      value: "bpr.subjectArea.readLearning",
      label: t("bpr.subjectArea.readLearning"),
    },
    {
      value: "bpr.subjectArea.chemistry",
      label: t("bpr.subjectArea.chemistry"),
    },
    {
      value: "bpr.subjectArea.law",
      label: t("bpr.subjectArea.law"),
    },
    {
      value: "bpr.subjectArea.worldDiscovery",
      label: t("bpr.subjectArea.worldDiscovery"),
    },
    {
      value: "bpr.subjectArea.economy",
      label: t("bpr.subjectArea.economy"),
    },
    {
      value: "bpr.subjectArea.mediaEducation",
      label: t("bpr.subjectArea.mediaEducation"),
    },
    {
      value: "bpr.subjectArea.musicEducation",
      label: t("bpr.subjectArea.musicEducation"),
    },
    {
      value: "bpr.subjectArea.sportEducation",
      label: t("bpr.subjectArea.sportEducation"),
    },
    {
      value: "bpr.subjectArea.citizenshipEducation",
      label: t("bpr.subjectArea.citizenshipEducation"),
    },
    {
      value: "bpr.subjectArea.geography",
      label: t("bpr.subjectArea.geography"),
    },
    {
      value: "bpr.subjectArea.history",
      label: t("bpr.subjectArea.history"),
    },
    {
      value: "bpr.subjectArea.artHistory",
      label: t("bpr.subjectArea.artHistory"),
    },
    {
      value: "bpr.subjectArea.ComputerScience",
      label: t("bpr.subjectArea.ComputerScience"),
    },
    {
      value: "bpr.subjectArea.languages",
      label: t("bpr.subjectArea.languages"),
    },
    {
      value: "bpr.subjectArea.italian",
      label: t("bpr.subjectArea.italian"),
    },
    {
      value: "bpr.subjectArea.spanish",
      label: t("bpr.subjectArea.spanish"),
    },
    {
      value: "bpr.subjectArea.french",
      label: t("bpr.subjectArea.french"),
    },
    {
      value: "bpr.subjectArea.german",
      label: t("bpr.subjectArea.german"),
    },
    {
      value: "bpr.subjectArea.english",
      label: t("bpr.subjectArea.english"),
    },
    {
      value: "bpr.subjectArea.ancientLanguages",
      label: t("bpr.subjectArea.ancientLanguages"),
    },
    {
      value: "bpr.subjectArea.literature",
      label: t("bpr.subjectArea.literature"),
    },
    {
      value: "bpr.subjectArea.mathematics",
      label: t("bpr.subjectArea.mathematics"),
    },
    {
      value: "bpr.subjectArea.vocationalGuidance",
      label: t("bpr.subjectArea.vocationalGuidance"),
    },
    {
      value: "bpr.subjectArea.philosohppy",
      label: t("bpr.subjectArea.philosohppy"),
    },
    {
      value: "bpr.subjectArea.physics",
      label: t("bpr.subjectArea.physics"),
    },
    {
      value: "bpr.subjectArea.politicalSscience",
      label: t("bpr.subjectArea.politicalSscience"),
    },
    {
      value: "bpr.subjectArea.sociology",
      label: t("bpr.subjectArea.sociology"),
    },
    {
      value: "bpr.subjectArea.biology",
      label: t("bpr.subjectArea.biology"),
    },
    {
      value: "bpr.subjectArea.geology",
      label: t("bpr.subjectArea.geology"),
    },
    {
      value: "bpr.subjectArea.technology",
      label: t("bpr.subjectArea.technology"),
    },
    {
      value: "bpr.other",
      label: t("bpr.other"),
    },
  ];

  const languageOptions: OptionsType[] = [
    { value: "de_DE", label: t("de_DE") },
    { value: "en_EN", label: t("en_EN") },
    { value: "ar_DZ", label: t("ar_DZ") },
    { value: "es_ES", label: t("es_ES") },
    { value: "fr_FR", label: t("fr_FR") },
    { value: "it_IT", label: t("it_IT") },
    { value: "ja_JP", label: t("ja_JP") },
    { value: "zh_CN", label: t("zh_CN") },
    { value: "pt_PT", label: t("pt_PT") },
    { value: "ru_RU", label: t("ru_RU") },
    { value: "bpr.other", label: t("bpr.other") },
  ];

  const ageOptions: string[] = [
    "3",
    "4",
    "5",
    "6",
    "7",
    "8",
    "9",
    "10",
    "11",
    "12",
    "13",
    "14",
    "15",
    "16",
    "17",
    "18",
  ];

  return {
    activityTypeOptions,
    subjectAreaOptions,
    languageOptions,
    ageOptions,
  };
}
