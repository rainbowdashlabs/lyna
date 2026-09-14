<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {type DownloadPage, listDownloads} from '~/api/account'
import {todayIsoDate, toIsoDate} from '~/util/format'

definePageMeta({layout: 'account'})

const route = useRoute()

const data = ref<DownloadPage | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

/** Ninety days back, which is the range the page opens on. */
const ninetyDaysAgo = new Date()
ninetyDaysAgo.setDate(ninetyDaysAgo.getDate() - 90)

const from = ref(toIsoDate(ninetyDaysAgo))
const to = ref(todayIsoDate())
const product = ref<number | null>(null)
const source = ref<string | null>(null)
const page = ref(1)

const licenseFilter = computed(() => {
  const raw = route.query.license
  const parsed = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

const pageCount = computed(() => {
  if (!data.value) return 1
  return Math.max(1, Math.ceil(data.value.totalRows / data.value.pageSize))
})

async function load() {
  loading.value = true
  errorMessage.value = null
  try {
    data.value = await listDownloads({
      from: from.value,
      to: to.value,
      product: product.value,
      source: source.value,
      license: licenseFilter.value,
      page: page.value,
    })
  } catch (e) {
    errorMessage.value = (e as Error).message ?? 'Failed to load downloads'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch([from, to, product, source], () => {
  page.value = 1
  void load()
})
watch(page, load)
</script>

<template>
  <div>
    <PageHeader class="mb-6">
      Downloads
    </PageHeader>
    <MutedText v-if="licenseFilter" class="mb-4 block" size="sm">
      Showing one license only.
      <NuxtLink class="text-primary hover:underline" to="/account/downloads">Show everything</NuxtLink>
    </MutedText>
    <DownloadFilters
        v-model:from="from"
        v-model:product="product"
        v-model:source="source"
        v-model:to="to"
        :products="data?.products ?? []"
    />
    <AsyncSection
        :empty="!!data && data.rows.length === 0"
        :error="errorMessage ?? undefined"
        :loading="loading"
        empty-message="No downloads in this date range."
    >
      <div v-if="data">
        <DownloadTable :rows="data.rows"/>
        <Pager v-model="page" :page-count="pageCount" :total="data.totalRows"/>
      </div>
    </AsyncSection>
  </div>
</template>
