import { useState, type ReactNode } from "react";

import useHasWorkflow from "@hooks/useWorkflows";
import { Logo } from "@ode-react-ui/core";
import { useClickOutside, useTitle } from "@ode-react-ui/hooks";
import { RafterDown } from "@ode-react-ui/icons";
import {
  Search,
  Community,
  Disconnect,
  Home,
  NeoMessaging,
  MyApps,
  NeoAssistance as Assistance,
  NewRelease,
  OneMessaging,
  OneProfile,
  Profile,
  Userbook,
} from "@ode-react-ui/icons/nav";
import clsx from "clsx";
import { type ISession } from "ode-ts-client";

interface NavLinkProps<T> {
  /**
   * href link
   */
  link: T;
  /**
   * To override default classes
   */
  className?: T;
  /**
   * Children props
   */
  children: ReactNode;
  /**
   * Translate Text
   */
  translate?: T;
  /**
   * Give Navlink Button Style (for 1D navbar)
   */
  button?: boolean;
}

type i18nParams = Record<string, any>;

interface HeaderProps {
  is1d?: boolean;
  src: string;
  i18n: (key: string, params?: i18nParams | undefined) => string;
  session: ISession;
}

function NavLink({
  link,
  className,
  children,
  button,
  translate,
}: NavLinkProps<string>) {
  const classes = clsx(className, {
    "nav-link": !button,
    button,
  });

  return (
    <a href={link} className={classes}>
      {children}
      {translate && <span>{translate}</span>}
    </a>
  );
}

export const Header = ({
  is1d = false,
  src = "",
  i18n,
  session,
}: HeaderProps): JSX.Element => {
  const collapseRef = useClickOutside(() => setIsCollapsed(true));

  const { workflows } = useHasWorkflow(session);

  const [isCollapsed, setIsCollapsed] = useState<boolean>(true);
  const welcomeUser = "Bonjour Support ONE, bienvenue !";
  const unreadNotification = 2;
  const title = useTitle();

  function toggleCollapsedNav() {
    setIsCollapsed(!isCollapsed);
  }

  const classes = clsx("header header-react", {
    "no-2d": is1d,
    "no-1d": !is1d,
  });

  return (
    <header className={classes}>
      {is1d ? (
        <div className="container-fluid">
          <nav className="navbar">
            <a className="navbar-title d-md-none text-truncate h4" href="/">
              {title}
            </a>
            <div className="d-none d-md-inline-flex gap-12 align-items-center">
              <Profile className="icon profile" />
              <span className="navbar-text">{welcomeUser}</span>
            </div>
            <ul
              className="navbar-nav"
              aria-hidden="false"
              aria-label={i18n("navbar.main.navigation")}
              role="menubar"
            >
              {workflows.conversation && (
                <li className="nav-item">
                  <a href="/" className="nav-link">
                    <OneMessaging className="icon notification" />
                    <span className="position-absolute badge rounded-pill bg-danger">
                      {unreadNotification}
                      <span className="visually-hidden">
                        {i18n("navbar.messages")}
                      </span>
                    </span>
                  </a>
                </li>
              )}
              <li className="nav-item">
                <a href="/userbook/mon-compte" className="nav-link">
                  <OneProfile className="icon user" />
                  <span className="visually-hidden">
                    {i18n("navbar.myaccount")}
                  </span>
                </a>
              </li>
              <li className="nav-item">
                <a href="/" className="nav-link">
                  <Assistance className="icon help" />
                  <span className="visually-hidden">{i18n("navbar.help")}</span>
                </a>
              </li>
              <li className="nav-item">
                <a href="/" className="nav-link">
                  <Disconnect className="icon logout" />
                  <span className="visually-hidden">
                    {i18n("navbar.disconnect")}
                  </span>
                </a>
              </li>
              <li className="nav-item d-md-none">
                <button
                  ref={collapseRef}
                  className="nav-link btn btn-naked"
                  type="button"
                  aria-controls="navbarCollapsed"
                  aria-expanded={!isCollapsed}
                  aria-label={i18n("navbar.secondary.navigation")}
                  onClick={toggleCollapsedNav}
                >
                  <RafterDown
                    className="icon rafter-down"
                    width="20"
                    height="20"
                  />
                </button>
              </li>
            </ul>
          </nav>
          <nav
            className="no-2d navbar navbar-secondary navbar-expand-md"
            aria-label={i18n("navbar.secondary.navigation")}
          >
            <div
              className={`collapse navbar-collapse ${
                !isCollapsed ? "show" : ""
              }`}
              id="navbarCollapsed"
            >
              <Logo
                is1d
                src={`${src}/img/illustrations/logo.png`}
                translate={i18n("navbar.home")}
              />

              <ul className="navbar-nav gap-8">
                <li className="nav-item">
                  <a href="/" className="button">
                    <NewRelease color="#fff" className="d-md-none" />
                    <span className="d-inline-block">
                      {i18n("portal.header.navigation.whatsnew")}
                    </span>
                  </a>
                </li>
                <li className="nav-item">
                  <a href="/" className="button">
                    <Userbook color="#fff" className="d-md-none" />
                    <span className="d-inline-block">
                      {i18n("portal.header.navigation.classMembers")}
                    </span>
                  </a>
                </li>
                <li className="nav-item">
                  <a href="/" className="button">
                    <MyApps color="#fff" className="d-md-none" />
                    <span className="d-inline-block">
                      {i18n("portal.header.navigation.myapps")}
                    </span>
                  </a>
                </li>
              </ul>
            </div>
          </nav>
        </div>
      ) : (
        <nav className="navbar navbar-expand-md">
          <div className="container-fluid">
            <Logo src={`${src}/img/illustrations/logo.png`} />
            <a href="/" className="navbar-title d-md-none">
              {title}
            </a>
            <div className="navbar-nav">
              <NavLink link="/" translate={i18n("navbar.home")}>
                <Home color="#fff" />
              </NavLink>
              <NavLink link="/welcome" translate="Applications">
                <MyApps color="#fff" />
              </NavLink>
              {workflows.conversation && (
                <NavLink link="/" translate="Conversation">
                  <NeoMessaging color="#fff" />
                </NavLink>
              )}
              <NavLink link="/" translate="Assistance">
                <Assistance color="#fff" />
              </NavLink>
              <div className="dropdown">
                <button
                  ref={collapseRef}
                  className="nav-link btn btn-naked d-md-none"
                  type="button"
                  aria-controls="dropdown-navbar"
                  aria-expanded={!isCollapsed}
                  // TODO: add i18n key
                  // "Ouvrir sous-menu"
                  aria-label={i18n("navbar.open.menu")}
                  onClick={toggleCollapsedNav}
                >
                  <RafterDown
                    className="icon rafter-down"
                    width="20"
                    height="20"
                    color="#fff"
                  />
                </button>
                <div
                  className={`dropdown-menu dropdown-menu-end ${
                    !isCollapsed ? "show" : ""
                  }`}
                  id="dropdown-navbar"
                >
                  {workflows.community && (
                    <NavLink
                      link="/"
                      className="dropdown-item"
                      translate={i18n("navbar.community")}
                    >
                      <Community className="icon community" />
                    </NavLink>
                  )}
                  {workflows.search && (
                    <NavLink
                      link="/searchengine"
                      className="dropdown-item"
                      translate={i18n("navbar.search")}
                    >
                      <Search className="icon search" />
                    </NavLink>
                  )}
                  <NavLink
                    link="/userbook/mon-compte"
                    className="dropdown-item"
                    translate={i18n("navbar.myaccount")}
                  >
                    <Profile className="icon user" />
                  </NavLink>
                  <hr className="dropdown-divider" />
                  <NavLink
                    link="/"
                    className="dropdown-item"
                    translate={i18n("navbar.disconnect")}
                  >
                    <Disconnect className="icon logout" />
                  </NavLink>
                </div>
              </div>
            </div>
          </div>
        </nav>
      )}
    </header>
  );
};
