/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import type {MailBlock} from '~/api/admin'

const {t} = useI18n()

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
        block.value = {...block.value, items: value.split('\n').map(line => line.trim()).filter(Boolean)}
    },
})

const centred = computed({
    get: () => (block.value.type === 'heading' || block.value.type === 'paragraph') && block.value.align === 'center',
    set: (value: boolean) => {
        if (block.value.type !== 'heading' && block.value.type !== 'paragraph') return
        block.value = {...block.value, align: value ? 'center' : undefined}
    },
})

const emphasis = computed({
    get: () => block.value.type === 'paragraph' && block.value.emphasis === true,
    set: (value: boolean) => {
        if (block.value.type !== 'paragraph') return
        block.value = {...block.value, emphasis: value || undefined}
    },
})
</script>

<template>
  <div class="space-y-2">
    <template v-if="block.type === 'heading'">
      <LabelledField :help="t('ui.mailBlockField.placeholdersWorkHereALinkIs')" :label="t('ui.mailBlockField.heading')">
        <TextInput v-model="block.text"/>
      </LabelledField>
      <label class="flex items-center gap-2 text-sm"><CheckboxInput v-model="centred"/> {{ t('ui.mailBlockField.centred') }}</label>
    </template>

    <template v-else-if="block.type === 'paragraph'">
      <LabelledField :help="t('ui.mailBlockField.placeholdersWorkHereALinkIs')" :label="t('ui.mailBlockField.text')">
        <TextAreaInput v-model="block.text" :rows="3"/>
      </LabelledField>
      <div class="flex flex-wrap gap-4">
        <label class="flex items-center gap-2 text-sm"><CheckboxInput v-model="centred"/> {{ t('ui.mailBlockField.centred') }}</label>
        <label class="flex items-center gap-2 text-sm"><CheckboxInput v-model="emphasis"/> {{ t('ui.mailBlockField.italics') }}</label>
      </div>
    </template>

    <LabelledField
        v-else-if="block.type === 'key'"
        :help="t('ui.mailBlockField.theLicenceKeyItselfIsFilled')"
        :label="t('ui.mailBlockField.labelAboveTheKey')"
    >
      <TextInput v-model="block.label"/>
    </LabelledField>

    <div v-else-if="block.type === 'button'" class="grid grid-cols-1 gap-2 sm:grid-cols-2">
      <LabelledField :label="t('ui.mailBlockField.buttonText')">
        <TextInput v-model="block.label"/>
      </LabelledField>
      <LabelledField :help="t('ui.mailBlockField.addressHelp')" :label="t('ui.mailBlockField.address')">
        <TextInput v-model="block.url" type="url"/>
      </LabelledField>
    </div>

    <LabelledField v-else-if="block.type === 'list'" :help="t('ui.mailBlockField.oneLinePerItem')" :label="t('ui.mailBlockField.items')">
      <TextAreaInput v-model="itemsText" :rows="3"/>
    </LabelledField>

    <div v-else-if="block.type === 'image'" class="grid grid-cols-1 gap-2 sm:grid-cols-2">
      <LabelledField :help="t('ui.mailBlockField.addressHelp')" :label="t('ui.mailBlockField.imageAddress')">
        <TextInput v-model="block.url"/>
      </LabelledField>
      <LabelledField :label="t('ui.mailBlockField.altText')">
        <TextInput v-model="block.alt"/>
      </LabelledField>
    </div>

    <MutedText v-else-if="block.type === 'links'" size="sm">{{ t('ui.mailBlockField.theGuildsLinks') }}</MutedText>

    <LabelledField
        v-else-if="block.type === 'raw'"
        :help="t('ui.mailBlockField.sentAsWrittenThisIsWhat')"
        :label="t('ui.mailBlockField.html')"
    >
      <TextAreaInput v-model="block.html" :rows="4" class="font-mono text-xs"/>
    </LabelledField>

    <MutedText v-else size="sm">{{ t('ui.mailBlockField.aLineAcrossTheMail') }}</MutedText>

    <MutedText v-if="block.requires?.length" class="block" size="xs">
      {{ t('ui.mailBlockField.leftOutUnless', {names: block.requires.join(', ')}) }}
    </MutedText>
  </div>
</template>
