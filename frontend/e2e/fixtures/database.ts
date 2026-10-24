/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {execFileSync} from 'node:child_process'

/**
 * The database of the stack the stories run against, for the few stories about time passing.
 *
 * <p>A key that is refreshed after seven days or expires after thirty cannot be waited for, and the
 * application has no endpoint for moving its clock, nor should it. So such a story moves the
 * timestamps instead, by running SQL in the stack's own database container.
 *
 * <p>The container is found from the backend's published port rather than from a project name: the
 * port is what the run was given, and the compose project the backend belongs to names the database
 * beside it. That holds for the per-checkout stacks `toolchain.sh` starts and for a bare compose run.
 */
const backendUrl = process.env.NUXT_BACKEND_URL || 'http://localhost:8890'

let container: string | null = null

function docker(...args: string[]): string {
    return execFileSync('docker', args, {encoding: 'utf-8'}).trim()
}

function databaseContainer(): string {
    if (container) return container
    const port = new URL(backendUrl).port || '80'
    const project = docker('ps', '--filter', `publish=${port}`, '--format', '{{.Label "com.docker.compose.project"}}')
        .split('\n')[0]
    if (!project) throw new Error(`No container publishes the backend's port ${port}`)
    const name = docker('ps',
        '--filter', `label=com.docker.compose.project=${project}`,
        '--filter', 'label=com.docker.compose.service=postgres_e2e',
        '--format', '{{.Names}}').split('\n')[0]
    if (!name) throw new Error(`The compose project ${project} runs no postgres_e2e`)
    container = name
    return name
}

/**
 * Runs one statement against the stack's database.
 *
 * @return what it printed, one row per line and columns separated by `|`
 */
export function sql(statement: string): string {
    return docker('exec', '-i', databaseContainer(), 'psql', '-U', 'user', '-d', 'lyna', '-v', 'ON_ERROR_STOP=1', '-tAc', statement)
}
