<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {overview} from '~/api/account'
import {publicTheme, type PublicTheme, saveAppearance} from '~/api/theme'
import {useTheme} from '~/composables/useTheme'
import {DarkMode, type DarkModeValue, Feel, type FeelValue} from '~/theme/themes'

definePageMeta({layout: 'account'})

const {activeTheme, activeFeel, darkMode, setTheme, setFeel, setDarkMode} = useTheme()

const policy = ref<PublicTheme | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const saveMessage = ref<string | null>(null)

const theme = ref('lyna')
const feel = ref<FeelValue>(Feel.ROUNDED)
const mode = ref<DarkModeValue>(DarkMode.SYSTEM)

const feelLocked = computed(() => !!policy.value && (policy.value.lockFeel || !policy.value.allowUserFeel))
const themeLocked = computed(() => !!policy.value && !policy.value.allowUserTheme)
const nothingToChoose = computed(() => themeLocked.value && feelLocked.value)

onMounted(async () => {
  try {
    const [settings, account] = await Promise.all([publicTheme(), overview()])
    policy.value = settings
    theme.value = account.account.theme ?? settings.defaultTheme
    feel.value = (account.account.feel ?? settings.defaultFeel) as FeelValue
    mode.value = (account.account.darkMode ?? darkMode.value) as DarkModeValue
  } catch (e) {
    errorMessage.value = (e as Error).message ?? 'Failed to load appearance settings'
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
  if (!feelLocked.value) setFeel(feel.value)
  setDarkMode(mode.value)
  try {
    await saveAppearance({theme: theme.value, feel: feel.value, darkMode: mode.value})
    saveMessage.value = 'Saved.'
  } catch {
    saveMessage.value = 'Could not save your choice.'
  }
}
</script>

<template>
  <div>
    <PageHeader class="mb-6">
      Appearance
    </PageHeader>
    <AsyncSection :error="errorMessage ?? undefined" :loading="loading">
      <div v-if="policy" class="space-y-6">
        <EmptyHint v-if="nothingToChoose">
          Your operator has fixed the theme and the corner style for everyone.
        </EmptyHint>

        <section v-if="!themeLocked">
          <CardHeader>Theme</CardHeader>
          <ThemeSelector v-model="theme" :enabled="policy.enabledThemes"/>
        </section>

        <section>
          <CardHeader>Dark mode</CardHeader>
          <SelectInput v-model="mode" class="max-w-xs">
            <option :value="DarkMode.SYSTEM">Follow the system</option>
            <option :value="DarkMode.LIGHT">Always light</option>
            <option :value="DarkMode.DARK">Always dark</option>
          </SelectInput>
        </section>

        <section>
          <CardHeader>Corners</CardHeader>
          <SelectInput v-model="feel" :disabled="feelLocked" class="max-w-xs">
            <option :value="Feel.ROUNDED">Rounded</option>
            <option :value="Feel.CORNERS">Square</option>
          </SelectInput>
          <MutedText v-if="feelLocked" class="mt-2 block" size="sm">
            Your operator has locked the corner style.
          </MutedText>
        </section>

        <div class="flex items-center gap-3">
          <PrimaryButton @click="save">Save</PrimaryButton>
          <MutedText v-if="saveMessage" :class="saveMessage === 'Saved.' ? 'text-success' : 'text-error'" size="sm">
            {{ saveMessage }}
          </MutedText>
        </div>
      </div>
    </AsyncSection>
  </div>
</template>
