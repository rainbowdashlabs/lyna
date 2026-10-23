/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {overview} from '~/api/account'
import {publicTheme, type PublicTheme, saveAppearance} from '~/api/theme'
import {useTheme} from '~/composables/useTheme'
import {DarkMode, type DarkModeValue} from '~/theme/themes'
import {effectiveTheme, offeredThemes} from '~/theme/policy'
import {usePageTitle} from '~/composables/usePageTitle'

const {t} = useI18n()
usePageTitle(t('layout.account.appearance'))

definePageMeta({layout: 'account'})

const {darkMode, setTheme, setDarkMode} = useTheme()

const policy = ref<PublicTheme | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const saveMessage = ref<string | null>(null)
const saveOk = ref(false)

const theme = ref('')
const mode = ref<DarkModeValue>(DarkMode.SYSTEM)

const themeLocked = computed(() => !!policy.value && !policy.value.allowUserTheme)
const offered = computed(() => offeredThemes(policy.value))

onMounted(async () => {
  try {
    const [settings, account] = await Promise.all([publicTheme(), overview()])
    policy.value = settings
    theme.value = effectiveTheme(settings, account.account.theme)
    mode.value = (account.account.darkMode ?? darkMode.value) as DarkModeValue
  } catch (e) {
    errorMessage.value = (e as Error).message || t('page.account.appearance.couldNotLoad')
  } finally {
    loading.value = false
  }
})

/**
 * Applies the choice here and stores it for the next visit. The backend applies the operator's
 * policy again on its side, so a locked choice changes nothing even if this page let it through.
 */
async function save() {
  saveMessage.value = null
  if (!themeLocked.value) setTheme(theme.value)
  setDarkMode(mode.value)
  try {
    await saveAppearance(themeLocked.value ? {darkMode: mode.value} : {theme: theme.value, darkMode: mode.value})
    saveOk.value = true
    saveMessage.value = t('page.account.appearance.saved')
  } catch {
    saveOk.value = false
    saveMessage.value = t('page.account.appearance.couldNotSave')
  }
}
</script>

<template>
  <div>
    <PageHeader class="mb-6">
      {{ t('page.account.appearance.appearance') }}
    </PageHeader>
    <AsyncSection :error="errorMessage ?? undefined" :loading="loading">
      <div v-if="policy" class="space-y-6">
        <EmptyHint v-if="themeLocked">
          {{ t('page.account.appearance.yourOperatorHasFixedTheTheme') }}
        </EmptyHint>

        <section v-if="!themeLocked">
          <CardHeader>{{ t('page.account.appearance.theme') }}</CardHeader>
          <ThemeSelector v-model="theme" :enabled="offered"/>
        </section>

        <section>
          <CardHeader>{{ t('page.account.appearance.darkMode') }}</CardHeader>
          <SelectInput v-model="mode" class="max-w-xs">
            <option :value="DarkMode.SYSTEM">{{ t('page.account.appearance.followTheSystem') }}</option>
            <option :value="DarkMode.LIGHT">{{ t('page.account.appearance.alwaysLight') }}</option>
            <option :value="DarkMode.DARK">{{ t('page.account.appearance.alwaysDark') }}</option>
          </SelectInput>
        </section>


        <div class="flex items-center gap-3">
          <PrimaryButton @click="save">{{ t('common.save') }}</PrimaryButton>
          <MutedText v-if="saveMessage" :class="saveOk ? 'text-success' : 'text-error'" size="sm">
            {{ saveMessage }}
          </MutedText>
        </div>
      </div>
    </AsyncSection>
  </div>
</template>
