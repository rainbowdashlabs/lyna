/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ChannelKind, DiscordChannel} from '~/api/adminDiscord'

/** A channel with what sits under it: the channels of a category, the threads of a channel. */
export interface ChannelNode {
    channel: DiscordChannel
    children: ChannelNode[]
}

/** One line of the open list, at the depth it is drawn. */
export interface ChannelRow {
    node: ChannelNode
    depth: number
    expanded: boolean
    /** Whether it is the last of its siblings, which ends the line a thread hangs from. */
    last: boolean
}

/** The icon Discord shows in front of each kind of channel. */
export const CHANNEL_ICONS: Record<ChannelKind, string[]> = {
    TEXT: ['fas', 'hashtag'],
    NEWS: ['fas', 'bullhorn'],
    FORUM: ['fas', 'comments'],
    THREAD: ['fas', 'message'],
    VOICE: ['fas', 'volume-high'],
    STAGE: ['fas', 'tower-broadcast'],
    CATEGORY: ['fas', 'folder'],
}

/**
 * Builds the sidebar from the flat list the backend sends: channels outside any category first,
 * then each category with its channels, each channel with its threads. A category that holds
 * nothing and cannot be picked itself is left out, as Discord leaves out one you cannot see into.
 */
export function buildTree(channels: DiscordChannel[]): ChannelNode[] {
    const nodes = new Map(channels.map(channel => [channel.id, {channel, children: [] as ChannelNode[]}]))
    const roots: ChannelNode[] = []
    for (const node of nodes.values()) {
        const parent = node.channel.parent ? nodes.get(node.channel.parent) : undefined
        if (parent) parent.children.push(node)
        else roots.push(node)
    }
    const loose = roots.filter(node => node.channel.kind !== 'CATEGORY')
    const categories = roots.filter(node => node.channel.kind === 'CATEGORY'
        && (node.children.length > 0 || node.channel.selectable))
    return [...loose, ...categories]
}

/** Every channel in the tree with the ones above it, top first. */
export function ancestry(roots: ChannelNode[], id: string): ChannelNode[] {
    for (const node of roots) {
        if (node.channel.id === id) return [node]
        const below = ancestry(node.children, id)
        if (below.length) return [node, ...below]
    }
    return []
}

function matches(node: ChannelNode, query: string): boolean {
    return node.channel.name.toLowerCase().includes(query)
        || node.children.some(child => matches(child, query))
}

/**
 * The lines the open list shows. A category is open unless collapsed; a channel with threads is
 * shut unless expanded. A search opens everything and keeps only what matches, with the channels
 * and categories it sits in.
 */
export function visibleRows(roots: ChannelNode[], collapsed: Set<string>, expanded: Set<string>, search: string): ChannelRow[] {
    const query = search.trim().toLowerCase()
    const rows: ChannelRow[] = []
    const walk = (nodes: ChannelNode[], depth: number) => {
        const shown = query ? nodes.filter(node => matches(node, query)) : nodes
        shown.forEach((node, index) => {
            const isCategory = node.channel.kind === 'CATEGORY'
            const open = node.children.length > 0 && (!!query
                || (isCategory ? !collapsed.has(node.channel.id) : expanded.has(node.channel.id)))
            rows.push({node, depth, expanded: open, last: index === shown.length - 1})
            if (open) walk(node.children, depth + 1)
        })
    }
    walk(roots, 0)
    return rows
}
