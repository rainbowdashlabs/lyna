/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {listLicenses, type LicenseList, type RoleAccessView} from '~/api/account'
import {usePageTitle} from '~/composables/usePageTitle'
import {productFamilies} from '~/util/productFamilies'

const {t} = useI18n()
usePageTitle(t('layout.account.licenses'))

definePageMeta({layout: 'account'})

const data = ref<LicenseList | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const tab = ref('owned')
const search = ref('')

async function load() {
  try {
    data.value = await listLicenses()
  } catch (e) {
    errorMessage.value = (e as Error).message ?? 'Failed to load licenses'
  } finally {
    loading.value = false
  }
}

const tabs = computed(() => [
  {key: 'owned', label: `Owned (${data.value?.owned.length ?? 0})`},
  {key: 'shared', label: `Shared with me (${data.value?.shared.length ?? 0})`},
  ...(data.value?.byRole.length ? [{key: 'role', label: `Via role (${data.value.byRole.length})`}] : []),
])

onMounted(async () => {
  await load()
  if (data.value && !data.value.owned.length && !data.value.shared.length && data.value.byRole.length) tab.value = 'role'
})

function matching<T extends {productName: string}>(entries: T[]): T[] {
  const term = search.value.trim().toLowerCase()
  if (!term) return entries
  return entries.filter(entry => entry.productName.toLowerCase().includes(term))
}

const shown = computed(() => matching(tab.value === 'owned' ? data.value?.owned ?? [] : data.value?.shared ?? []))
const families = computed(() => productFamilies(shown.value, license => license.productId, license => license.parentId))
const roleFamilies = computed(() => productFamilies(
    matching<RoleAccessView>(data.value?.byRole ?? []), access => access.productId, access => access.parentId))
const anyLicenses = computed(() =>
    (data.value?.owned.length ?? 0) + (data.value?.shared.length ?? 0) + (data.value?.byRole.length ?? 0) > 0)
</script>

<template>
  <div>
    <PageHeader class="mb-6">
      {{ t('page.account.licenses.licenses') }}
    </PageHeader>
    <RedeemKey class="mb-6" @redeemed="load"/>
    <AsyncSection :error="errorMessage ?? undefined" :loading="loading">
      <div v-if="data">
        <TabBar v-model="tab" :tabs="tabs" class="mb-4"/>
        <SearchInput v-if="anyLicenses" v-model="search" class="mb-4" :placeholder="t('page.account.licenses.searchByProduct')"/>
        <div v-if="tab === 'role'" class="space-y-2">
          <template v-for="family in roleFamilies" :key="family.productId">
            <RoleAccessRow v-for="access in family.heads" :key="access.productId" :access="access"/>
            <RoleAccessRow v-for="access in family.addons" :key="access.productId" :access="access" addon/>
          </template>
          <EmptyHint v-if="!roleFamilies.length">
            {{ search ? t('page.account.licenses.noLicenseMatchesThatName') : t('page.account.licenses.noRoleAccess') }}
          </EmptyHint>
        </div>
        <div v-else-if="shown.length" class="space-y-2">
          <template v-for="family in families" :key="family.productId">
            <LicenseRow v-for="license in family.heads" :key="license.id" :license="license"/>
            <NuxtLink
                v-if="!family.heads.length && family.addons[0]"
                :to="`/products/${family.productId}`"
                class="block px-3 text-sm text-(--text-muted) hover:text-(--text)"
            >
              {{ family.addons[0].parentName }}
            </NuxtLink>
            <LicenseRow v-for="license in family.addons" :key="license.id" :license="license" addon/>
          </template>
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
