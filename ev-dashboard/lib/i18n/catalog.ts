import account from "./messages/account";
import auth from "./messages/auth";
import badges from "./messages/badges";
import billing from "./messages/billing";
import cards from "./messages/cards";
import cars from "./messages/cars";
import charging from "./messages/charging";
import common from "./messages/common";
import labels from "./messages/labels";
import logs from "./messages/logs";
import notifications from "./messages/notifications";
import organization from "./messages/organization";
import overview from "./messages/overview";
import payments from "./messages/payments";
import pricing from "./messages/pricing";
import resources from "./messages/resources";
import roaming from "./messages/roaming";
import sessions from "./messages/sessions";
import shared from "./messages/shared";
import stations from "./messages/stations";
import statistics from "./messages/statistics";
import templates from "./messages/templates";
import users from "./messages/users";
import workspace from "./messages/workspace";
import type { Messages } from "./types";

export const catalog: Messages = {
  ...account,
  ...auth,
  ...badges,
  ...billing,
  ...cards,
  ...cars,
  ...charging,
  ...common,
  ...labels,
  ...logs,
  ...notifications,
  ...organization,
  ...overview,
  ...payments,
  ...pricing,
  ...resources,
  ...roaming,
  ...sessions,
  ...shared,
  ...stations,
  ...statistics,
  ...templates,
  ...users,
  ...workspace,
};
