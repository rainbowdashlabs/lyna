/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {randomUUID} from 'node:crypto'

/**
 * A name no other story, and no earlier run, has used.
 *
 * <p>The suite creates accounts through the real signup endpoint and never deletes them, so a fixed
 * address would collide with itself the second time the suite ran. Making each one unique is what
 * lets the stack keep its database between runs instead of needing a reset endpoint that only exists
 * for the tests.
 */
export function uniqueEmail(prefix: string): string {
    return `${prefix}-${randomUUID()}@example.invalid`
}
