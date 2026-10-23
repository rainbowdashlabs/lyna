/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, nextTick, onBeforeUnmount, onMounted, ref, useId} from 'vue'
import type {DiscordChannel} from '~/api/adminDiscord'
import {BORDERED_INPUT_CLASSES} from '../inputClasses'
import {ancestry, buildTree, CHANNEL_ICONS, type ChannelRow, visibleRows} from './channelTree'

/**
 * A channel picked from a list laid out like Discord's sidebar: categories that fold, channels
 * under them, and threads under their channel, shut until opened. Typing searches every name and
 * opens whatever holds a match.
 *
 * <p>The keys are those of a tree: up and down move, right opens and left shuts or climbs to the
 * parent, enter picks, escape closes. A channel that is only there to hold threads opens on enter
 * instead of being picked.
 *
 * <p>Clicks inside the open list have their default cancelled. The field usually sits in a label,
 * and once a pick has removed the row that was clicked, WebKit hands the click to the label, which
 * clicks the field and opens the list again.
 */
const props = defineProps<{ channels: DiscordChannel[], placeholder: string, noneLabel?: string }>()
const channelId = defineModel<string>({required: true})

const {t} = useI18n()
const uid = useId()
const open = ref(false)
const search = ref('')
const collapsed = ref(new Set<string>())
const expanded = ref(new Set<string>())
const activeIndex = ref(0)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const searchInput = ref<HTMLInputElement | null>(null)

type Item = { none: true } | { none: false, row: ChannelRow }

const tree = computed(() => buildTree(props.channels))
const path = computed(() => channelId.value ? ancestry(tree.value, channelId.value) : [])
const selected = computed(() => path.value.at(-1)?.channel)
const context = computed(() => path.value.slice(0, -1).map(node => node.channel.name).join(' › '))
const items = computed<Item[]>(() => [
  ...(props.noneLabel && !search.value.trim() ? [{none: true} as const] : []),
  ...visibleRows(tree.value, collapsed.value, expanded.value, search.value).map(row => ({none: false, row}) as const),
])

function domId(item: Item): string {
  return `${uid}-${item.none ? 'none' : item.row.node.channel.id}`
}

const activeId = computed(() => {
  const item = items.value[activeIndex.value]
  return item ? domId(item) : undefined
})

function isSelected(item: Item): boolean {
  return item.none ? !channelId.value : item.row.node.channel.id === channelId.value
}

function toggle(id: string, kind: string) {
  const set = kind === 'CATEGORY' ? collapsed : expanded
  const next = new Set(set.value)
  if (!next.delete(id)) next.add(id)
  set.value = next
}

function choose(id: string) {
  channelId.value = id
  close()
}

async function show() {
  const next = new Set(expanded.value)
  path.value.slice(0, -1).forEach(node => next.add(node.channel.id))
  expanded.value = next
  collapsed.value = new Set([...collapsed.value].filter(id => !path.value.some(node => node.channel.id === id)))
  search.value = ''
  open.value = true
  activeIndex.value = Math.max(0, items.value.findIndex(isSelected))
  await nextTick()
  searchInput.value?.focus()
  reveal()
}

function close() {
  open.value = false
  trigger.value?.focus()
}

function reveal() {
  const item = items.value[activeIndex.value]
  if (item) nextTick(() => document.getElementById(domId(item))?.scrollIntoView({block: 'nearest'}))
}

function move(to: number) {
  activeIndex.value = Math.min(Math.max(to, 0), items.value.length - 1)
  reveal()
}

function activate(item: Item | undefined) {
  if (!item) return
  if (item.none) return choose('')
  const {channel, children} = item.row.node
  if (channel.selectable) choose(channel.id)
  else if (children.length) toggle(channel.id, channel.kind)
}

function sideways(direction: 'in' | 'out', event: KeyboardEvent) {
  const item = items.value[activeIndex.value]
  if (search.value || !item || item.none) return
  event.preventDefault()
  const {row} = item
  const foldable = row.node.children.length > 0
  if (direction === 'in' && foldable && !row.expanded) return toggle(row.node.channel.id, row.node.channel.kind)
  if (direction === 'in' && row.expanded) return move(activeIndex.value + 1)
  if (direction === 'out' && row.expanded) return toggle(row.node.channel.id, row.node.channel.kind)
  if (direction === 'out' && row.node.channel.parent) {
    move(items.value.findIndex(other => !other.none && other.row.node.channel.id === row.node.channel.parent))
  }
}

function onKey(event: KeyboardEvent) {
  const keys: Record<string, () => void> = {
    ArrowDown: () => move(activeIndex.value + 1),
    ArrowUp: () => move(activeIndex.value - 1),
    PageDown: () => move(activeIndex.value + 8),
    PageUp: () => move(activeIndex.value - 8),
    Enter: () => activate(items.value[activeIndex.value]),
    Escape: close,
  }
  if (event.key === 'ArrowRight') return sideways('in', event)
  if (event.key === 'ArrowLeft') return sideways('out', event)
  if (event.key === 'Tab') return void (open.value = false)
  const action = keys[event.key]
  if (!action) return
  event.preventDefault()
  action()
}

function onSearch() {
  const query = search.value.trim().toLowerCase()
  const pickable = (item: Item) => !item.none && item.row.node.channel.selectable
  const hit = items.value.findIndex(item => pickable(item) && !item.none && item.row.node.channel.name.toLowerCase().includes(query))
  activeIndex.value = Math.max(0, hit >= 0 ? hit : items.value.findIndex(pickable))
}

function onClickOutside(event: MouseEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) open.value = false
}

onMounted(() => document.addEventListener('mousedown', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('mousedown', onClickOutside))
</script>

<template>
  <div ref="root" class="relative">
    <button
        ref="trigger"
        :aria-controls="`${uid}-list`"
        :aria-expanded="open"
        :class="[BORDERED_INPUT_CLASSES, 'flex w-full items-center gap-2 text-left']"
        aria-haspopup="listbox"
        type="button"
        @click="open ? close() : show()"
        @keydown.down.prevent="show"
    >
      <template v-if="selected">
        <font-awesome-icon :icon="CHANNEL_ICONS[selected.kind]" class="h-3.5 w-3.5 shrink-0 text-(--text-muted)"/>
        <span class="truncate">{{ selected.name }}</span>
        <span v-if="context" class="font-data ml-auto shrink-0 truncate pl-2 text-[11px] text-(--text-muted) uppercase">{{ context }}</span>
      </template>
      <template v-else-if="channelId">
        <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="h-3.5 w-3.5 shrink-0 text-(--text-muted)"/>
        <span class="truncate">{{ t('ui.discordPicker.unknownChannel') }}</span>
        <span class="font-data ml-auto shrink-0 pl-2 text-[11px] text-(--text-muted)">{{ channelId }}</span>
      </template>
      <span v-else class="flex-1 text-(--text-muted)">{{ noneLabel ?? placeholder }}</span>
      <font-awesome-icon :class="selected || channelId ? '' : 'ml-auto'" :icon="['fas', 'chevron-down']" class="h-3 w-3 shrink-0 text-(--text-muted)"/>
    </button>

    <div v-if="open" class="rounded-theme absolute z-30 mt-1 flex max-h-96 w-full min-w-64 flex-col border border-(--border) bg-(--bg) shadow-lg" @click.prevent>
      <div class="border-b border-(--border) p-2">
        <input
            ref="searchInput"
            v-model="search"
            :aria-activedescendant="activeId"
            :aria-controls="`${uid}-list`"
            :class="[BORDERED_INPUT_CLASSES, 'w-full py-1 text-sm']"
            :placeholder="t('ui.discordPicker.search')"
            role="combobox"
            type="search"
            @input="onSearch"
            @keydown="onKey"
        />
      </div>
      <div :id="`${uid}-list`" class="overflow-y-auto py-1" role="listbox">
        <template v-for="(item, index) in items" :key="domId(item)">
          <ChannelNoneRow v-if="item.none" :id="domId(item)" :active="index === activeIndex" :label="noneLabel!" :selected="isSelected(item)" @select="choose('')"/>
          <ChannelTreeRow
              v-else
              :active="index === activeIndex"
              :dom-id="domId(item)"
              :row="item.row"
              :selected="isSelected(item)"
              @select="choose(item.row.node.channel.id)"
              @toggle="toggle(item.row.node.channel.id, item.row.node.channel.kind)"
          />
        </template>
        <MutedText v-if="!items.length" class="block px-3 py-2" size="sm" tag="p">{{ t('ui.discordPicker.noMatch') }}</MutedText>
      </div>
    </div>
  </div>
</template>
