/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {MailBlock} from '~/api/admin'

const block = defineModel<MailBlock>({required: true})

/**
 * A list is edited as one line per item, because that is how somebody writes a list. Splitting on
 * save rather than keeping an array of inputs means adding and removing a line is typing, not
 * clicking.
 */
const itemsText = computed({
    get: () => block.value.type === 'list' ? block.value.items.join('\n') : '',
    set: (value: string) => {
        if (block.value.type !== 'list') return
        block.value = {type: 'list', items: value.split('\n').map(line => line.trim()).filter(Boolean)}
    },
})
</script>

<template>
  <div class="space-y-2">
    <LabelledField
        v-if="block.type === 'heading'"
        help="Placeholders: {{ name }} {{ key }} {{ product }} {{ downloadUrl }}"
        label="Heading"
    >
      <TextInput v-model="block.text"/>
    </LabelledField>

    <LabelledField
        v-else-if="block.type === 'paragraph'"
        help="Placeholders work here. A link is written [label](https://…)."
        label="Text"
    >
      <TextAreaInput v-model="block.text" :rows="3"/>
    </LabelledField>

    <LabelledField
        v-else-if="block.type === 'key'"
        help="The licence key itself is filled in when the mail is sent."
        label="Label above the key"
    >
      <TextInput v-model="block.label"/>
    </LabelledField>

    <div v-else-if="block.type === 'button'" class="grid grid-cols-1 gap-2 sm:grid-cols-2">
      <LabelledField label="Button text">
        <TextInput v-model="block.label"/>
      </LabelledField>
      <LabelledField help="Has to be an http address. {{ downloadUrl }} works here." label="Address">
        <TextInput v-model="block.url" type="url"/>
      </LabelledField>
    </div>

    <LabelledField v-else-if="block.type === 'list'" help="One line per item." label="Items">
      <TextAreaInput v-model="itemsText" :rows="3"/>
    </LabelledField>

    <LabelledField
        v-else-if="block.type === 'raw'"
        help="Sent as written. This is what a mail composed before the editor kept."
        label="HTML"
    >
      <TextAreaInput v-model="block.html" :rows="4" class="font-mono text-xs"/>
    </LabelledField>

    <MutedText v-else size="sm">A line across the mail.</MutedText>
  </div>
</template>
