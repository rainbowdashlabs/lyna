/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import Th from '@/components/table/Th.vue'
import {sortIconFor, type AriaSort, type SortDirection, type SortKey} from '@/composables/useSortable'

/**
 * Header cell of a sortable column. The whole cell toggles the sort, the icon stays a real button
 * so the column remains reachable by keyboard.
 */
const props = defineProps<{
  label: string
  sortKey: SortKey
  activeKey: SortKey
  direction: SortDirection
  align?: 'left' | 'center' | 'right'
}>()

const emit = defineEmits<{
  sort: [key: SortKey]
}>()

const {t} = useI18n()

const active = computed(() => props.activeKey === props.sortKey)
const icon = computed(() => sortIconFor(active.value, props.direction))
const ariaSort = computed<AriaSort>(() => {
  if (!active.value) return 'none'
  return props.direction === 'asc' ? 'ascending' : 'descending'
})
</script>

<template>
  <Th :align="align" :aria-sort="ariaSort" class="cursor-pointer select-none" @click="emit('sort', sortKey)">
    <span class="inline-flex items-center gap-1">
      <slot>{{ label }}</slot>
      <IconButton
          :icon="['fas', icon]"
          :label="t('tableFilter.sortBy', {column: label})"
          :class="active ? 'text-primary' : 'text-(--text-muted) hover:text-primary'"
          class="-my-2"
      />
    </span>
  </Th>
</template>
