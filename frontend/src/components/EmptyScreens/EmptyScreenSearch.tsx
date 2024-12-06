import { EmptyScreen, useEdificeClient } from '@edifice.io/react';
import illuSearch from '@images/emptyscreen/illu-search.svg';
import { useTranslation } from 'react-i18next';

export default function EmptyScreenSearch(): JSX.Element {
  const { appCode } = useEdificeClient();
  const { t } = useTranslation();

  return (
    <EmptyScreen
      imageSrc={illuSearch}
      imageAlt={t('explorer.emptyScreen.search.alt', { ns: appCode })}
      text={t('explorer.emptyScreen.search.text', { ns: appCode })}
    />
  );
}
