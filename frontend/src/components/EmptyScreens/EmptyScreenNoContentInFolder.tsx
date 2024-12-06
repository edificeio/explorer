import { EmptyScreen, useEdificeClient } from '@edifice.io/react';
import illuNoContentInFolder from '@images/emptyscreen/illu-no-content-in-folder.svg';
import { useTranslation } from 'react-i18next';

export default function EmptyScreenNoContentInFolder(): JSX.Element | null {
  const { appCode } = useEdificeClient();
  const { t } = useTranslation();

  return (
    <EmptyScreen
      imageSrc={illuNoContentInFolder}
      imageAlt={t('explorer.emptyScreen.folder.empty.alt', { ns: appCode })}
      text={t('explorer.emptyScreen.label', { ns: appCode })}
    />
  );
}
