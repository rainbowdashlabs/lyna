<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {lookupRegistration, type RegistrationInfo} from '~/api/admin'
import PrimaryButton from '~/components/button/PrimaryButton.vue'

const {t} = useI18n()

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))
const discordIdInput = ref('')
const result = ref<RegistrationInfo | null>(null)
const errorMessage = ref<string | null>(null)
const busy = ref(false)

watch(() => route.params.guildId, (next) => {
  guildId.value = String(next)
  result.value = null
})

async function submit() {
  if (!discordIdInput.value.trim()) return
  busy.value = true
  errorMessage.value = null
  try {
    result.value = await lookupRegistration(guildId.value, discordIdInput.value.trim())
  } catch (e) {
    const error = e as {response?: {data?: string, status?: number}}
    if (error.response?.status === 404) errorMessage.value = t('page.admin.g.guildId.registrations.noRegistrationFound')
    else errorMessage.value = typeof error.response?.data === 'string' ? error.response.data : 'Lookup failed.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader class="mb-4">
      {{ t('page.admin.g.guildId.registrations.registrations') }}
    </PageHeader>
    <p class="mb-4 text-sm opacity-70">
      {{ t('page.admin.g.guildId.registrations.lookUpARegistrationByDiscord') }}
    </p>
    <form class="mb-4 flex gap-2" @submit.prevent="submit">
      <TextInput v-model="discordIdInput" class="flex-1" inputmode="numeric" :placeholder="t('page.admin.g.guildId.registrations.discordUserId')"/>
      <PrimaryButton :disabled="busy" @click="submit">
        {{ busy ? 'Looking up…' : 'Look up' }}
      </PrimaryButton>
    </form>
    <div v-if="errorMessage" class="text-sm text-error">
      {{ errorMessage }}
    </div>
    <div
        v-if="result"
        class="space-y-4 rounded-theme border border-border-light dark:border-border-dark p-4 text-sm"
    >
      <div>
        <div>
          <span class="opacity-70">{{ t('page.admin.g.guildId.registrations.discordId') }}</span> {{ result.discordId }}
        </div>
        <div>
          <span class="opacity-70">{{ t('page.admin.g.guildId.registrations.memberName') }}</span> {{ result.memberName ?? '— not in this guild —' }}
        </div>
      </div>
      <section>
        <CardHeader>
          Owned ({{ result.ownedLicenses.length }})
        </CardHeader>
        <ul v-if="result.ownedLicenses.length" class="divide-y divide-border-light dark:divide-border-dark">
          <li v-for="l in result.ownedLicenses" :key="l.id" class="py-1">
            <span class="font-medium">{{ l.productName }}</span>
            <span class="ml-2 opacity-60">id {{ l.id }} · {{ l.identifier }} · {{ l.shareeCount }} sharees</span>
          </li>
        </ul>
        <div v-else class="opacity-60">
          {{ t('page.admin.g.guildId.registrations.none') }}
        </div>
      </section>
      <section>
        <CardHeader>
          Shared with ({{ result.sharedLicenses.length }})
        </CardHeader>
        <ul v-if="result.sharedLicenses.length" class="divide-y divide-border-light dark:divide-border-dark">
          <li v-for="l in result.sharedLicenses" :key="l.id" class="py-1">
            <span class="font-medium">{{ l.productName }}</span>
            <span class="ml-2 opacity-60">id {{ l.id }} · owner {{ l.owner }}</span>
          </li>
        </ul>
        <div v-else class="opacity-60">
          {{ t('page.admin.g.guildId.registrations.none') }}
        </div>
      </section>
    </div>
  </div>
</template>
