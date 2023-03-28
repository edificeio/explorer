import { useOdeClient } from "@ode-react-ui/core";
import { type OptionsType } from "@ode-react-ui/core/dist/Form/Select";

export default function usePublishLibraryModalOptions() {
  const { i18n } = useOdeClient();

  const activityTypeOptions: OptionsType[] = [
    {
      value: "bpr.activityType.classroomActivity",
      label: i18n("bpr.activityType.classroomActivity"),
    },
    {
      value: "bpr.activityType.groupActivity",
      label: i18n("bpr.activityType.groupActivity"),
    },
    {
      value: "bpr.activityType.personalActivity",
      label: i18n("bpr.activityType.personalActivity"),
    },
    {
      value: "bpr.activityType.homework",
      label: i18n("bpr.activityType.homework"),
    },
    {
      value: "bpr.activityType.exercize",
      label: i18n("bpr.activityType.exercize"),
    },
    {
      value: "bpr.activityType.learningPath",
      label: i18n("bpr.activityType.learningPath"),
    },
    {
      value: "bpr.activityType.courseElement",
      label: i18n("bpr.activityType.courseElement"),
    },
    {
      value: "bpr.other",
      label: i18n("bpr.other"),
    },
  ];

  const subjectAreaOptions: OptionsType[] = [
    {
      value: "bpr.subjectArea.artActivity",
      label: i18n("bpr.subjectArea.artActivity"),
    },
    {
      value: "bpr.subjectArea.readLearning",
      label: i18n("bpr.subjectArea.readLearning"),
    },
    {
      value: "bpr.subjectArea.chemistry",
      label: i18n("bpr.subjectArea.chemistry"),
    },
    {
      value: "bpr.subjectArea.law",
      label: i18n("bpr.subjectArea.law"),
    },
    {
      value: "bpr.subjectArea.worldDiscovery",
      label: i18n("bpr.subjectArea.worldDiscovery"),
    },
    {
      value: "bpr.subjectArea.economy",
      label: i18n("bpr.subjectArea.economy"),
    },
    {
      value: "bpr.subjectArea.mediaEducation",
      label: i18n("bpr.subjectArea.mediaEducation"),
    },
    {
      value: "bpr.subjectArea.musicEducation",
      label: i18n("bpr.subjectArea.musicEducation"),
    },
    {
      value: "bpr.subjectArea.sportEducation",
      label: i18n("bpr.subjectArea.sportEducation"),
    },
    {
      value: "bpr.subjectArea.citizenshipEducation",
      label: i18n("bpr.subjectArea.citizenshipEducation"),
    },
    {
      value: "bpr.subjectArea.geography",
      label: i18n("bpr.subjectArea.geography"),
    },
    {
      value: "bpr.subjectArea.history",
      label: i18n("bpr.subjectArea.history"),
    },
    {
      value: "bpr.subjectArea.artHistory",
      label: i18n("bpr.subjectArea.artHistory"),
    },
    {
      value: "bpr.subjectArea.ComputerScience",
      label: i18n("bpr.subjectArea.ComputerScience"),
    },
    {
      value: "bpr.subjectArea.languages",
      label: i18n("bpr.subjectArea.languages"),
    },
    {
      value: "bpr.subjectArea.italian",
      label: i18n("bpr.subjectArea.italian"),
    },
    {
      value: "bpr.subjectArea.spanish",
      label: i18n("bpr.subjectArea.spanish"),
    },
    {
      value: "bpr.subjectArea.french",
      label: i18n("bpr.subjectArea.french"),
    },
    {
      value: "bpr.subjectArea.german",
      label: i18n("bpr.subjectArea.german"),
    },
    {
      value: "bpr.subjectArea.english",
      label: i18n("bpr.subjectArea.english"),
    },
    {
      value: "bpr.subjectArea.ancientLanguages",
      label: i18n("bpr.subjectArea.ancientLanguages"),
    },
    {
      value: "bpr.subjectArea.literature",
      label: i18n("bpr.subjectArea.literature"),
    },
    {
      value: "bpr.subjectArea.mathematics",
      label: i18n("bpr.subjectArea.mathematics"),
    },
    {
      value: "bpr.subjectArea.vocationalGuidance",
      label: i18n("bpr.subjectArea.vocationalGuidance"),
    },
    {
      value: "bpr.subjectArea.philosohppy",
      label: i18n("bpr.subjectArea.philosohppy"),
    },
    {
      value: "bpr.subjectArea.physics",
      label: i18n("bpr.subjectArea.physics"),
    },
    {
      value: "bpr.subjectArea.politicalSscience",
      label: i18n("bpr.subjectArea.politicalSscience"),
    },
    {
      value: "bpr.subjectArea.sociology",
      label: i18n("bpr.subjectArea.sociology"),
    },
    {
      value: "bpr.subjectArea.biology",
      label: i18n("bpr.subjectArea.biology"),
    },
    {
      value: "bpr.subjectArea.geology",
      label: i18n("bpr.subjectArea.geology"),
    },
    {
      value: "bpr.subjectArea.technology",
      label: i18n("bpr.subjectArea.technology"),
    },
    {
      value: "bpr.other",
      label: i18n("bpr.other"),
    },
  ];

  const languageOptions: OptionsType[] = [
    { value: "de_DE", label: i18n("de_DE") },
    { value: "en_EN", label: i18n("en_EN") },
    { value: "ar_DZ", label: i18n("ar_DZ") },
    { value: "es_ES", label: i18n("es_ES") },
    { value: "fr_FR", label: i18n("fr_FR") },
    { value: "it_IT", label: i18n("it_IT") },
    { value: "ja_JP", label: i18n("ja_JP") },
    { value: "zh_CN", label: i18n("zh_CN") },
    { value: "pt_PT", label: i18n("pt_PT") },
    { value: "ru_RU", label: i18n("ru_RU") },
    { value: "bpr.other", label: i18n("bpr.other") },
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
