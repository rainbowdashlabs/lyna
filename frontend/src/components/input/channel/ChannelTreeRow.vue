/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import {CHANNEL_ICONS, type ChannelRow} from './channelTree'

/**
 * One line of the channel list, drawn the way Discord's sidebar draws it: a category as a small
 * capitalised heading that folds, a channel with its icon, a thread hanging off its channel on a
 * branch line. What has children carries a chevron of its own, so opening it never picks it.
 */
const props = defineProps<{ row: ChannelRow, domId: string, selected: boolean, active: boolean }>()
const emit = defineEmits<{ select: [], toggle: [] }>()

const {t} = useI18n()
const channel = computed(() => props.row.node.channel)
const isCategory = computed(() => channel.value.kind === 'CATEGORY')
const isThread = computed(() => channel.value.kind === 'THREAD')
const foldable = computed(() => props.row.node.children.length > 0)

function pick() {
  if (channel.value.selectable) emit('select')
  else if (foldable.value) emit('toggle')
}
</script>

<template>
  <div
      :id="domId"
      :aria-selected="selected"
      :aria-expanded="foldable ? row.expanded : undefined"
      :class="[
        isCategory ? 'mt-3 first:mt-0' : '',
        isThread ? 'pl-9' : 'pl-1',
        selected ? 'bg-secondary/12 text-(--text) shadow-[inset_3px_0_0_var(--color-secondary)]'
          : active ? 'bg-(--bg-accent) text-(--text)' : 'text-(--text-muted) hover:bg-(--bg-accent) hover:text-(--text)',
      ]"
      class="relative flex items-center pr-2"
      role="option"
      @mousedown.prevent
  >
    <template v-if="isThread">
      <span class="pointer-events-none absolute top-0 left-5 h-1/2 w-3 border-b border-l border-(--border)"/>
      <span v-if="!row.last" class="pointer-events-none absolute top-1/2 bottom-0 left-5 border-l border-(--border)"/>
    </template>
    <button
        v-if="!isThread"
        :aria-label="row.expanded ? t('ui.discordPicker.collapse') : t('ui.discordPicker.expand')"
        :class="foldable ? 'hover:text-(--text)' : 'invisible'"
        :tabindex="-1"
        class="flex h-7 w-5 shrink-0 items-center justify-center"
        type="button"
        @click="emit('toggle')"
    >
      <font-awesome-icon :icon="['fas', row.expanded ? 'chevron-down' : 'chevron-right']" class="h-2.5 w-2.5"/>
    </button>
    <button
        :class="[
          channel.selectable || foldable ? 'cursor-pointer' : 'cursor-default',
          channel.selectable ? '' : 'opacity-70',
          isCategory ? 'font-data text-[11px] font-semibold tracking-wider uppercase' : 'text-sm',
        ]"
        :tabindex="-1"
        class="flex min-w-0 flex-1 items-center gap-2 py-1.5 text-left"
        type="button"
        @click="pick"
    >
      <font-awesome-icon v-if="!isCategory" :icon="CHANNEL_ICONS[channel.kind]" class="h-3.5 w-3.5 shrink-0 opacity-70"/>
      <span class="truncate">{{ channel.name }}</span>
      <span v-if="channel.kind === 'FORUM' && channel.selectable" class="ml-auto shrink-0 pl-2 text-[11px] opacity-70">
        {{ t('ui.discordPicker.forum') }}
      </span>
    </button>
  </div>
</template>
