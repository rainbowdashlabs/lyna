/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
function pad2(n: number): string {
    return String(n).padStart(2, '0')
}

const CALENDAR_DATE = /^\d{4}-\d{2}-\d{2}$/
const CLOCK = /^(\d{1,2}):(\d{2})(:\d{2})?(\.\d+)?$/

/**
 * Reads what a value says about a point on the calendar.
 *
 * <p>A moment and a calendar date look alike and are not. `2026-10-12T18:00:00Z` is a moment, and
 * which day it falls on depends on where the reader stands. `2026-10-12` is a day and stands for
 * that day everywhere; read as a moment it becomes midnight in London, which is the day before
 * anywhere west of it. So a bare date is anchored to the reader's own midnight, and everything
 * that writes a date reads it back through here.
 */
function asDate(value: string): Date {
    return CALENDAR_DATE.test(value) ? new Date(`${value}T00:00:00`) : new Date(value)
}

/**
 * Formats a moment, or a plain clock reading such as `18:00:00`, as `HH:mm`. Returns an empty
 * string when the input is missing.
 *
 * <p>A moment is read in the runtime's own time zone, which is where the reader is: an appointment
 * stored as `17:30Z` is half past seven to somebody in Berlin in summer, and that is what they are
 * told. A plain clock carries no zone and is simply shortened.
 */
export function formatTime(value?: string | null): string {
    if (!value) return ''
    const clock = CLOCK.exec(value)
    if (clock) return `${pad2(Number(clock[1]))}:${clock[2]}`
    const d = new Date(value)
    if (Number.isNaN(d.getTime())) return ''
    return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

/**
 * Formats a moment or a calendar date as `dd.MM.yyyy`. Returns an empty string when the input is
 * missing or is not a date at all.
 */
export function formatDate(value?: string | null): string {
    if (!value) return ''
    const date = asDate(value)
    if (Number.isNaN(date.getTime())) return ''
    return date.toLocaleDateString('de-DE', {
        day: '2-digit', month: '2-digit', year: 'numeric',
    })
}

/**
 * Formats a moment or a calendar date with its weekday in front - `Montag, 12.10.2026` - for the
 * places that name a single day and want it recognised at a glance. Returns an empty string when
 * the input is missing.
 *
 * @param value   the moment or calendar date
 * @param weekday whether the weekday is written out or shortened
 */
export function formatWeekdayDate(value?: string | null, weekday: 'long' | 'short' = 'long'): string {
    if (!value) return ''
    const date = asDate(value)
    if (Number.isNaN(date.getTime())) return ''
    return date.toLocaleDateString('de-DE', {
        weekday, day: '2-digit', month: '2-digit', year: 'numeric',
    })
}

/**
 * Formats a moment or a calendar date as `dd.MM.` for the narrow places, such as the deadline chip
 * on a board card, where the year would take room the card has not got. Returns an empty string
 * when the input is missing.
 */
export function formatDayMonth(value?: string | null): string {
    if (!value) return ''
    const date = asDate(value)
    if (Number.isNaN(date.getTime())) return ''
    return date.toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit'})
}

const WEEKDAYS = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

/**
 * The name of a weekday given as a number the way the calendar counts them, 1 for Monday through
 * 7 for Sunday. Anything outside that has no name.
 */
export function weekdayName(dayOfWeek: number): string {
    return WEEKDAYS[dayOfWeek] ?? ''
}

/**
 * Formats a moment or a calendar date as a long German date - `27. Juli 2026` - for editorial
 * surfaces such as blog posts and release notes. Returns an empty string when the input is missing.
 */
export function formatDateLong(value?: string | null): string {
    if (!value) return ''
    const date = asDate(value)
    if (Number.isNaN(date.getTime())) return ''
    return date.toLocaleDateString('de-DE', {
        year: 'numeric', month: 'long', day: 'numeric',
    })
}

/**
 * Formats a moment as `dd.MM.yyyy, HH:mm` in the reader's own time zone. Returns an empty string
 * when the input is missing. Mirrors the most common date and time display used across views.
 */
export function formatDateTime(value?: string | null): string {
    if (!value) return ''
    const date = asDate(value)
    if (Number.isNaN(date.getTime())) return ''
    return date.toLocaleString('de-DE', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
    })
}

/**
 * Formats an ISO timestamp as a German relative time - "gerade eben", "vor 5 Min.",
 * "vor 3 Std.", "vor 2 Tagen" - falling back to the absolute date after 30 days.
 * Returns an empty string when the input is missing.
 */
export function formatRelative(iso?: string | null): string {
    if (!iso) return ''
    const diffMs = Date.now() - new Date(iso).getTime()
    const diffMin = Math.floor(diffMs / 60000)
    if (diffMin < 1) return 'gerade eben'
    if (diffMin < 60) return `vor ${diffMin} Min.`
    const diffH = Math.floor(diffMin / 60)
    if (diffH < 24) return `vor ${diffH} Std.`
    const diffD = Math.floor(diffH / 24)
    if (diffD <= 30) return `vor ${diffD} Tag${diffD > 1 ? 'en' : ''}`
    return formatDate(iso)
}

/**
 * Turns what a date field holds (`yyyy-MM-dd`) into the instant an endpoint expecting a timestamp
 * will accept, which is midnight UTC on that day.
 *
 * <p>A date field and a timestamp field look interchangeable and are not: handed a bare `2026-03-17`
 * a backend parsing instants refuses the whole request, so the field silently loses whatever was
 * typed into it. Anything writing a date into a timestamp goes through here.
 *
 * @param date a calendar date, or nothing
 * @return the ISO instant, or null when there was no date
 */
export function dateToInstant(date?: string | null): string | null {
    if (!date) return null
    const parsed = new Date(`${date}T00:00:00Z`)
    return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString()
}

/**
 * The counterpart of {@link dateToInstant}: the day a stored instant falls on in UTC, ready to be
 * put back into a date field. Returns an empty string when there is nothing, which is what an empty
 * date field holds.
 *
 * <p>Not for showing anybody a date. This is the day the server names an instant by, which is what
 * makes it the right thing to send back and the wrong thing to put on a page: use {@link toIsoDate}
 * where the reader's own day is meant.
 */
export function instantToDate(iso?: string | null): string {
    if (!iso) return ''
    const parsed = new Date(iso)
    if (Number.isNaN(parsed.getTime())) return ''
    return parsed.toISOString().slice(0, 10)
}

/**
 * The calendar date a moment falls on where the reader stands, as `yyyy-MM-dd`.
 *
 * <p>Not the same as the date part of the stored moment. A drill at half past midnight is stored as
 * half past ten the evening before in London, and a page that took the day off that string put the
 * drill on the wrong day for everybody east of it. Anything that needs the day of a moment, to look
 * something up by it or to hand it back as a date, asks here.
 */
export function toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

/** Today where the reader stands, as `yyyy-MM-dd`. */
export function todayIsoDate(): string {
    return toIsoDate(new Date())
}

/**
 * A moment as a `datetime-local` field holds it, which is `yyyy-MM-ddTHH:mm` on the reader's own
 * clock. Returns an empty string when there is nothing, which is what an empty field holds.
 *
 * <p>The field has no time zone: whatever stands in it is read back as local time. Putting the
 * stored moment in unchanged therefore shows the London clock and, once saved again, moves the
 * value by the difference every single time.
 */
export function instantToLocalInput(iso?: string | null): string {
    if (!iso) return ''
    const parsed = new Date(iso)
    if (Number.isNaN(parsed.getTime())) return ''
    return `${toIsoDate(parsed)}T${pad2(parsed.getHours())}:${pad2(parsed.getMinutes())}`
}

/**
 * The moment a `datetime-local` field holds, as a stored instant. Returns an empty string when the
 * field holds nothing or something no clock could read.
 *
 * <p>The inverse of {@link instantToLocalInput}: what stands in the field is the reader's own clock,
 * so it is read as local time and only then turned into the moment that is stored.
 */
export function localInputToInstant(value?: string | null): string {
    if (!value) return ''
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) return ''
    return parsed.toISOString()
}

/**
 * A time of day, `HH:mm`, placed on the day another moment falls on, as a stored instant.
 *
 * <p>This is how a time typed beside a member reaches the sheet it belongs to. Reading it onto
 * today instead is what once moved an arrival written on last week's sheet a week forward, and made
 * every hour counted from it wrong.
 */
export function timeOnDayOf(day: string | null | undefined, time: string): string {
    if (!time) return ''
    const base = day ? new Date(day) : new Date()
    if (Number.isNaN(base.getTime())) return ''
    const moment = new Date(`${toIsoDate(base)}T${time}`)
    if (Number.isNaN(moment.getTime())) return ''
    return moment.toISOString()
}

/**
 * Formats a byte count as a compact human-readable size (`B`, `KB`, `MB`), using one decimal
 * place for the kilobyte and megabyte ranges.
 */
export function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
