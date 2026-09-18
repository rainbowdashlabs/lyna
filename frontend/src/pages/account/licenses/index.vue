<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {listLicenses, type LicenseList, type LicenseView} from '~/api/account'

const {t} = useI18n()

definePageMeta({layout: 'account'})

const data = ref<LicenseList | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const tab = ref('owned')
const search = ref('')

onMounted(async () => {
  try {
    data.value = await listLicenses()
  } catch (e) {
    errorMessage.value = (e as Error).message ?? 'Failed to load licenses'
  } finally {
    loading.value = false
  }
})

const tabs = computed(() => [
  {key: 'owned', label: `Owned (${data.value?.owned.length ?? 0})`},
  {key: 'shared', label: `Shared with me (${data.value?.shared.length ?? 0})`},
])

function matching(licenses: LicenseView[]): LicenseView[] {
  const term = search.value.trim().toLowerCase()
  if (!term) return licenses
  return licenses.filter(license => license.productName.toLowerCase().includes(term))
}

const shown = computed(() => matching(tab.value === 'owned' ? data.value?.owned ?? [] : data.value?.shared ?? []))
const anyLicenses = computed(() => (data.value?.owned.length ?? 0) + (data.value?.shared.length ?? 0) > 0)
</script>

<template>
  <div>
    <PageHeader class="mb-6">
      {{ t('page.account.licenses.licenses') }}
    </PageHeader>
    <AsyncSection :error="errorMessage ?? undefined" :loading="loading">
      <div v-if="data">
        <TabBar v-model="tab" :tabs="tabs" class="mb-4"/>
        <SearchInput v-if="anyLicenses" v-model="search" class="mb-4" :placeholder="t('page.account.licenses.searchByProduct')"/>
        <div v-if="shown.length" class="space-y-2">
          <LicenseRow v-for="license in shown" :key="license.id" :license="license"/>
        </div>
        <EmptyHint v-else-if="search">{{ t('page.account.licenses.noLicenseMatchesThatName') }}</EmptyHint>
        <EmptyHint v-else-if="tab === 'owned'">
          {{ t('page.account.licenses.youDonTOwnAnyLicenses') }}
          <NuxtLink class="text-primary hover:underline" to="/">{{ t('page.account.licenses.storefront') }}</NuxtLink>.
        </EmptyHint>
        <EmptyHint v-else>{{ t('page.account.licenses.noOneHasSharedALicense') }}</EmptyHint>
      </div>
    </AsyncSection>
  </div>
</template>
