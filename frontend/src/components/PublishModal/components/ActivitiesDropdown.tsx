import { Dropdown } from "@edifice-ui/react";
import { t } from "i18next";
import { Control, Controller } from "react-hook-form";

import { useActivitiesOptions } from "../hooks/useActivitiesOptions";
import { FormDataProps } from "../hooks/usePublishModal";

export const ActivitiesDropdown = ({
  control,
  selectedActivities,
  selectActivities,
}: {
  control: Control<FormDataProps, any>;
  selectedActivities: Array<string | number>;
  selectActivities: (value: string | number) => void;
}) => {
  const activitiesOptions = useActivitiesOptions();
  return (
    <div className="col d-flex">
      <Controller
        name="activityType"
        control={control}
        rules={{
          required: true,
        }}
        render={({ field: { onChange } }) => {
          return (
            <Dropdown block overflow>
              <Dropdown.Trigger
                size="md"
                label={t("bpr.form.publication.type")}
                badgeContent={selectedActivities?.length}
              />
              <Dropdown.Menu>
                {activitiesOptions.map((option, index) => (
                  <Dropdown.CheckboxItem
                    key={index}
                    value={option.value}
                    model={selectedActivities}
                    onChange={() => {
                      selectActivities(option.value);
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
