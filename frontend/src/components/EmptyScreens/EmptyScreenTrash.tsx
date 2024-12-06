import { EmptyScreen, useEdificeClient } from '@edifice.io/react';
import illuTrash from '@images/emptyscreen/illu-trash.svg';
import { useTranslation } from 'react-i18next';

export default function EmptyScreenTrash() {
  const { appCode } = useEdificeClient();
  const { t } = useTranslation();

  return (
    <EmptyScreen
      imageSrc={illuTrash}
      imageAlt={t('explorer.emptyScreen.trash.alt')}
      title={t('explorer.emptyScreen.trash.title')}
      text={t('explorer.emptyScreen.trash.empty', { ns: appCode })}
    />
  );
}
