import { Dropdown } from "@edifice-ui/react";
import { t } from "i18next";
import { Control, Controller } from "react-hook-form";

import { FormDataProps } from "../hooks/usePublishModal";
import { useSubjectsOptions } from "../hooks/useSubjectsOptions";

export const SubjectsDropdown = ({
  control,
  selectedSubjectAreas,
  selectSubjects,
}: {
  control: Control<FormDataProps, any>;
  selectedSubjectAreas: Array<string | number>;
  selectSubjects: (value: string | number) => void;
}) => {
  const subjectsOptions = useSubjectsOptions();

  return (
    <div className="col d-flex">
      <Controller
        name="subjectArea"
        control={control}
        rules={{
          required: true,
        }}
        render={({ field: { onChange } }) => {
          return (
            <Dropdown block overflow>
              <Dropdown.Trigger
                size="md"
                label={t("bpr.form.publication.discipline")}
                badgeContent={selectedSubjectAreas?.length}
              />
              <Dropdown.Menu>
                {subjectsOptions.map((option, index) => (
                  <Dropdown.CheckboxItem
                    key={index}
                    value={option.value}
                    model={selectedSubjectAreas}
                    onChange={() => {
                      selectSubjects(option.value);
                      onChange(option.value);
                    }}
                  >
                    {option.label}
                  </Dropdown.CheckboxItem>
                ))}
              </Dropdown.Menu>
            </Dropdown>
          );
        }}
      />
    </div>
  );
};
