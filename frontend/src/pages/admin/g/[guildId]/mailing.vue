<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {listMailings, type MailingTemplate} from '~/api/admin'
import Spinner from '~/components/feedback/Spinner.vue'

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))

const templates = ref<MailingTemplate[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    templates.value = await listMailings(guildId.value)
  } finally {
    loading.value = false
  }
}

watch(() => route.params.guildId, (next) => {
  guildId.value = String(next)
  load()
})

onMounted(load)
</script>

<template>
  <div>
    <PageHeader class="mb-4">
      Mailing templates
    </PageHeader>
    <p class="mb-4 text-sm opacity-70">
      Templates currently configured per product. Edit body text with the
      <code class="font-mono">/mailing</code> slash command until the editor lands on the web.
    </p>
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <ul v-else-if="templates.length" class="divide-y divide-border-light dark:divide-border-dark rounded-theme border border-border-light dark:border-border-dark text-sm">
      <li v-for="t in templates" :key="t.id" class="p-3">
        <div class="font-medium">
          {{ t.name }}
        </div>
        <div class="text-xs opacity-60">
          {{ t.productName }} · template id {{ t.id }}
        </div>
      </li>
    </ul>
    <div v-else class="rounded-theme border border-border-light dark:border-border-dark p-8 text-center opacity-70">
      No mailing templates configured.
    </div>
  </div>
</template>
