import angular from "angular";
import { APP } from "ode-ts-client";
import { OdeModules, conf } from 'ode-ngjs-front';
import { AppController } from "./controller";
import * as ExplorerApp from './directives/explorer.directive';

angular.module("app", [OdeModules.getBase(), OdeModules.getI18n(), OdeModules.getUi(), OdeModules.getWidgets()])
.controller("appCtrl", ['$scope', AppController])
.directive("explorer", ExplorerApp.DirectiveFactory)

conf().Platform.apps.initialize(APP.EXPLORER);