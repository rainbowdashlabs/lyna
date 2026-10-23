/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {InstanceAppearance} from '~/api/admin'
import {computed} from 'vue'
import {THEMES} from '~/theme/themes'
import {MODE_TOKENS, PAGE_TOKENS} from '~/theme/policy'

const {t} = useI18n()

const data = defineModel<InstanceAppearance>({required: true})

defineProps<{
    saving: boolean
    saveMessage: string | null
    /** Whether the last save went through, which decides how its message is coloured. */
    saveOk: boolean
}>()

const emit = defineEmits<{
    save: []
}>()

const customThemeColors = computed({
    get: () => data.value.customThemeColorsJson ?? '',
    set: (value: string) => {
        data.value.customThemeColorsJson = value.trim() ? value : null
    },
})

const enabledThemes = computed({
    get: () => data.value.enabledThemes.join(', '),
    set: (value: string) => {
        data.value.enabledThemes = value.split(',').map(entry => entry.trim()).filter(Boolean)
    },
})
</script>

<template>
  <NeutralContainer>
    <form class="space-y-4" @submit.prevent="emit('save')">
      <LabelledField :label="t('ui.instanceAppearanceForm.defaultTheme')">
        <SelectInput v-model="data.defaultTheme">
          <option v-for="(theme, key) in THEMES" :key="key" :value="key">{{ theme.label }}</option>
        </SelectInput>
      </LabelledField>
      <LabelledField
          :help="t('ui.instanceAppearanceForm.emptyMeansEveryThemeIsAvailable', {themes: Object.keys(THEMES).join(', ')})"
          :label="t('ui.instanceAppearanceForm.enabledThemesCommaSeparated')"
      >
        <TextInput v-model="enabledThemes"/>
      </LabelledField>
      <div class="grid grid-cols-1 gap-1 text-sm">
        <FieldLabel inline><CheckboxInput v-model="data.allowUserTheme"/> {{ t('ui.instanceAppearanceForm.allowUserTheme') }}</FieldLabel>
        <MutedText size="sm" tag="span">{{ t('ui.instanceAppearanceForm.allowUserThemeHelp') }}</MutedText>
      </div>
      <LabelledField
          :help="t('ui.instanceAppearanceForm.customThemeColorsHelp', {pageTokens: PAGE_TOKENS.join(', '), modeTokens: MODE_TOKENS.join(', ')})"
          :label="t('ui.instanceAppearanceForm.customThemeColorsJson')"
      >
        <TextAreaInput v-model="customThemeColors" :rows="6" class="font-mono text-xs"/>
      </LabelledField>
      <MutedText v-if="saveMessage" :class="saveOk ? 'text-success' : 'text-error'" size="sm" tag="p">
        {{ saveMessage }}
      </MutedText>
      <SaveButton :action="() => emit('save')" :disabled="saving"/>
    </form>
  </NeutralContainer>
</template>
