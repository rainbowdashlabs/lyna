/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** The parts of a product an admin manages, each behind a tab of the product's page. */
export const PRODUCT_ADMIN_TABS = ['general', 'page', 'downloads', 'access', 'announcements'] as const

export type ProductAdminTab = typeof PRODUCT_ADMIN_TABS[number]
