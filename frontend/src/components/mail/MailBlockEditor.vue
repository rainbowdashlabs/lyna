/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {MailBlock} from '~/api/admin'

const {t} = useI18n()

const blocks = defineModel<MailBlock[]>({required: true})

/**
 * What an operator can add. Deliberately short: the mail is rendered into a fixed layout, and a
 * block nobody can style wrongly is a mail that arrives looking the way it was meant to.
 */
const TYPES: {type: MailBlock['type']; label: string; make: () => MailBlock}[] = [
    {type: 'heading', label: 'Heading', make: () => ({type: 'heading', text: 'Thank you'})},
    {type: 'paragraph', label: 'Text', make: () => ({type: 'paragraph', text: 'Hi {{ name }},'})},
    {type: 'key', label: 'Licence key', make: () => ({type: 'key', label: 'Your licence key'})},
    {type: 'button', label: 'Button', make: () => ({type: 'button', label: 'Download', url: '{{ downloadUrl }}'})},
    {type: 'list', label: 'List', make: () => ({type: 'list', items: ['First', 'Second']})},
    {type: 'divider', label: 'Divider', make: () => ({type: 'divider'})},
]

function labelFor(block: MailBlock): string {
    return TYPES.find(entry => entry.type === block.type)?.label
        ?? (block.type === 'raw' ? 'HTML, written before the editor' : block.type)
}

function add(make: () => MailBlock) {
    blocks.value = [...blocks.value, make()]
}

function remove(index: number) {
    blocks.value = blocks.value.filter((_, at) => at !== index)
}

function move(index: number, by: number) {
    const target = index + by
    if (target < 0 || target >= blocks.value.length) return
    const next = [...blocks.value]
    const [moved] = next.splice(index, 1)
    next.splice(target, 0, moved!)
    blocks.value = next
}
</script>

<template>
  <div class="space-y-3">
    <ol v-if="blocks.length" class="space-y-3">
      <li v-for="(block, index) in blocks" :key="index">
        <NeutralContainer>
          <header class="mb-2 flex items-center justify-between gap-2">
            <SectionLabel>{{ labelFor(block) }}</SectionLabel>
            <div class="flex gap-1">
              <SecondaryButton :disabled="index === 0" compact @click="move(index, -1)">↑</SecondaryButton>
              <SecondaryButton :disabled="index === blocks.length - 1" compact @click="move(index, 1)">↓</SecondaryButton>
              <ErrorButton compact @click="remove(index)">{{ t('common.remove') }}</ErrorButton>
            </div>
          </header>
          <MailBlockField :model-value="block" @update:model-value="blocks[index] = $event"/>
        </NeutralContainer>
      </li>
    </ol>
    <EmptyHint v-else>{{ t('ui.mailBlockEditor.nothingInThisMailYetAdd') }}</EmptyHint>

    <div class="flex flex-wrap gap-2">
      <SecondaryButton v-for="entry in TYPES" :key="entry.type" compact @click="add(entry.make)">
        Add {{ entry.label.toLowerCase() }}
      </SecondaryButton>
    </div>
  </div>
</template>
