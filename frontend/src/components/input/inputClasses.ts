/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Canonical class list for the standard bordered text-style field
 * (text input, password, number, date/time, select, textarea, …).
 * Any visible field that isn't a search bar should pull its base classes
 * from here so border, focus ring, padding, and disabled state stay in
 * lock-step across the form library.
 *
 * Layout (width, height, resize-y, …) is up to the caller — append it
 * with `${BORDERED_INPUT_CLASSES} w-full` etc.
 */
export const BORDERED_INPUT_CLASSES =
    'px-3 py-2 rounded-theme border border-bg-light-accent bg-bg-light text-(--text)' +
    ' transition-colors duration-150 outline-none' +
    ' focus:border-primary focus:ring-1 focus:ring-primary' +
    ' disabled:opacity-50 disabled:cursor-not-allowed' +
    ' dark:border-bg-dark-accent dark:bg-bg-dark'

/**
 * Inline borderless variant — same focus / disabled language as
 * {@link BORDERED_INPUT_CLASSES} but no border by default, hover swap
 * to {@code --bg-accent}, and a tighter pad for inline editing inside
 * tables or cells.
 */
export const BORDERLESS_INPUT_CLASSES =
    'px-2 py-1 bg-transparent text-(--text) transition-colors duration-150 outline-none' +
    ' rounded-theme hover:bg-(--bg-accent) focus:bg-(--bg)' +
    ' focus:ring-1 focus:ring-primary' +
    ' disabled:opacity-50 disabled:cursor-not-allowed'
