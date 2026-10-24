/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import type {KeyTypeDraft} from '~/api/adminUpdateTokens'

/**
 * The settings of a product key type: what buyers see it called, how they hand a key to a server, and
 * how keys of it are rotated, expire and are counted.
 */
const draft = defineModel<KeyTypeDraft>({required: true})

const {t} = useI18n()

/** The command as the field edits it; an empty field is no command. */
const command = computed({
  get: () => draft.value.command ?? '',
  set: (value: string) => {
    draft.value.command = value.trim() ? value : null
  },
})
</script>

<template>
  <div class="space-y-3">
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <LabelledField :label="t('ui.keyTypeFields.name')">
        <TextInput v-model="draft.name" data-testid="key-type-name" required/>
      </LabelledField>
      <LabelledField :label="t('ui.keyTypeFields.slug')">
        <TextInput v-model="draft.slug" class="font-data" data-testid="key-type-slug" pattern="[a-z0-9][a-z0-9-]{0,31}" required/>
      </LabelledField>
    </div>
    <LabelledField :label="t('ui.keyTypeFields.command')">
      <TextInput v-model="command" class="font-data" data-testid="key-type-command"/>
      <MutedText size="xs" tag="p">{{ t('ui.keyTypeFields.commandHint') }}</MutedText>
    </LabelledField>
    <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
      <LabelledField :label="t('ui.keyTypeFields.refreshDays')"><NumberInput v-model="draft.refreshDays"/></LabelledField>
      <LabelledField :label="t('ui.keyTypeFields.expiryDays')"><NumberInput v-model="draft.expiryDays"/></LabelledField>
      <LabelledField :label="t('ui.keyTypeFields.allowance')"><NumberInput v-model="draft.allowance"/></LabelledField>
      <LabelledField :label="t('ui.keyTypeFields.trialAllowance')"><NumberInput v-model="draft.trialAllowance"/></LabelledField>
    </div>
    <label class="flex items-center gap-2 text-sm">
      <CheckboxInput v-model="draft.enabled"/> {{ t('ui.keyTypeFields.enabled') }}
    </label>
  </div>
</template>
