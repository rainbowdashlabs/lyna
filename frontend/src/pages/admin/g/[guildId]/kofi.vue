<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {createKofi, listGuildProducts, listKofi, type KofiMapping, type ProductSummary} from '~/api/admin'
import PrimaryButton from '~/components/button/PrimaryButton.vue'
import Spinner from '~/components/feedback/Spinner.vue'

const {t} = useI18n()

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))

const mappings = ref<KofiMapping[]>([])
const products = ref<ProductSummary[]>([])
const loading = ref(true)

const linkCode = ref('')
const productId = ref<number | null>(null)
const busy = ref(false)
const message = ref<string | null>(null)
const isError = ref(false)

async function load() {
  loading.value = true
  try {
    [mappings.value, products.value] = await Promise.all([
      listKofi(guildId.value),
      listGuildProducts(guildId.value),
    ])
  } finally {
    loading.value = false
  }
}

watch(() => route.params.guildId, (next) => {
  guildId.value = String(next)
  load()
})

onMounted(load)

async function submit() {
  if (!linkCode.value || productId.value == null) return
  busy.value = true
  message.value = null
  isError.value = false
  try {
    await createKofi(guildId.value, linkCode.value.trim(), productId.value)
    linkCode.value = ''
    productId.value = null
    message.value = t('page.admin.g.guildId.kofi.mappingSaved')
    await load()
  } catch (e) {
    const err = e as {response?: {data?: string}}
    isError.value = true
    message.value = typeof err.response?.data === 'string' ? err.response.data : 'Save failed.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader class="mb-4">
      {{ t('page.admin.g.guildId.kofi.koFiMappings') }}
    </PageHeader>
    <p class="mb-4 text-sm opacity-70">
      {{ t('page.admin.g.guildId.kofi.mapKoFiDirectLinkCodes') }}
    </p>
    <section class="mb-4 rounded-theme border border-border-light dark:border-border-dark p-4">
      <CardHeader>
        {{ t('page.admin.g.guildId.kofi.addOrUpdateMapping') }}
      </CardHeader>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <LabelledField :label="t('page.admin.g.guildId.kofi.linkCode')">
          <TextInput v-model="linkCode"/>
        </LabelledField>
        <LabelledField :label="t('common.product')">
          <SelectInput v-model="productId">
            <option :value="null" disabled>{{ t('page.admin.g.guildId.kofi.choose') }}</option>
            <option v-for="p in products" :key="p.id" :value="p.id">{{ p.name }}</option>
          </SelectInput>
        </LabelledField>
      </div>
      <div v-if="message" class="mt-2 text-sm" :class="isError ? 'text-error' : 'text-success'">
        {{ message }}
      </div>
      <div class="mt-3">
        <PrimaryButton :disabled="busy" @click="submit">
          {{ busy ? 'Saving…' : 'Save' }}
        </PrimaryButton>
      </div>
    </section>

    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <ul v-else-if="mappings.length" class="divide-y divide-border-light dark:divide-border-dark rounded-theme border border-border-light dark:border-border-dark text-sm">
      <li v-for="m in mappings" :key="m.linkCode" class="flex items-center justify-between p-3">
        <div>
          <code class="font-mono">{{ m.linkCode }}</code>
          <span class="ml-2 opacity-60">→ {{ m.productName }} (id {{ m.productId }})</span>
        </div>
      </li>
    </ul>
    <div v-else class="rounded-theme border border-border-light dark:border-border-dark p-8 text-center opacity-70">
      {{ t('page.admin.g.guildId.kofi.noKoFiMappingsYet') }}
    </div>
  </div>
</template>
