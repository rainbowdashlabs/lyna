<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {directDownloadUrl, listReleaseTypes, listVersions, type KioskProduct, type ReleaseEntry, type VersionEntry} from '~/api/kiosk'
import PrimaryButton from '~/components/button/PrimaryButton.vue'
import SecondaryButton from '~/components/button/SecondaryButton.vue'
import Spinner from '~/components/feedback/Spinner.vue'

const props = defineProps<{
  product: KioskProduct | null
}>()

const emit = defineEmits<{
  close: []
}>()

type Step = 'releaseType' | 'version' | 'confirm'

const step = ref<Step>('releaseType')
const releaseTypes = ref<ReleaseEntry[]>([])
const versions = ref<VersionEntry[]>([])
const selectedReleaseType = ref<ReleaseEntry | null>(null)
const selectedVersion = ref<VersionEntry | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

watch(() => props.product, async (next) => {
  if (!next) return
  step.value = 'releaseType'
  selectedReleaseType.value = null
  selectedVersion.value = null
  versions.value = []
  error.value = null
  loading.value = true
  try {
    const list = await listReleaseTypes(next.id)
    releaseTypes.value = list
    if (list.length === 1) {
      await pickReleaseType(list[0]!)
    }
  } catch (e) {
    error.value = (e as Error).message ?? 'Failed to load release types'
  } finally {
    loading.value = false
  }
}, {immediate: true})

async function pickReleaseType(rt: ReleaseEntry) {
  if (!props.product) return
  selectedReleaseType.value = rt
  loading.value = true
  error.value = null
  try {
    versions.value = await listVersions(props.product.id, rt.id)
    if (versions.value.length === 0) {
      error.value = 'No versions available for this release type yet.'
      step.value = 'version'
      return
    }
    step.value = 'version'
  } catch (e) {
    error.value = (e as Error).message ?? 'Failed to load versions'
  } finally {
    loading.value = false
  }
}

function pickVersion(version: VersionEntry) {
  selectedVersion.value = version
  step.value = 'confirm'
}

const downloadHref = computed(() => {
  if (!props.product || !selectedReleaseType.value || !selectedVersion.value) return ''
  return directDownloadUrl(props.product.id, selectedReleaseType.value.id, selectedVersion.value.version)
})

function back() {
  if (step.value === 'confirm') step.value = 'version'
  else if (step.value === 'version') step.value = 'releaseType'
  else emit('close')
}

function close() {
  emit('close')
}

function formatPublished(seconds: number): string {
  if (!seconds) return ''
  return new Date(seconds * 1000).toISOString().replace(/\..+$/, '').replace('T', ' ')
}
</script>

<template>
  <div
      v-if="product"
      class="fixed inset-0 z-40 flex items-center justify-center bg-black/60 p-4"
      role="dialog"
      aria-modal="true"
      @click.self="close"
  >
    <div class="flex max-h-[90vh] w-full max-w-lg flex-col rounded-theme bg-bg-light dark:bg-bg-dark shadow-2xl">
      <header class="flex items-center justify-between border-b border-border-light dark:border-border-dark p-4">
        <h3 class="text-lg font-semibold">
          {{ product.name }}
        </h3>
        <button class="opacity-70 hover:opacity-100" aria-label="Close" @click="close">
          <font-awesome-icon :icon="['fas', 'xmark']" />
        </button>
      </header>

      <nav class="px-4 pt-3 text-xs opacity-70">
        <span :class="{'font-semibold opacity-100': step === 'releaseType'}">Release</span>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="mx-1 text-[0.6rem]" />
        <span :class="{'font-semibold opacity-100': step === 'version'}">Version</span>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="mx-1 text-[0.6rem]" />
        <span :class="{'font-semibold opacity-100': step === 'confirm'}">Download</span>
      </nav>

      <div class="flex-1 overflow-y-auto p-4">
        <div v-if="loading" class="flex items-center justify-center py-8">
          <Spinner size="lg" />
        </div>
        <div v-else-if="error" class="rounded-theme border border-error/30 bg-error/10 p-3 text-sm text-error">
          {{ error }}
        </div>

        <ul v-else-if="step === 'releaseType'" class="space-y-2">
          <li v-for="rt in releaseTypes" :key="rt.id">
            <button
                class="w-full rounded-theme border border-border-light dark:border-border-dark px-3 py-2 text-left transition-colors hover:bg-primary/10"
                @click="pickReleaseType(rt)"
            >
              <div class="font-medium">
                {{ rt.name }}
              </div>
              <div v-if="rt.description" class="text-xs opacity-70">
                {{ rt.description }}
              </div>
            </button>
          </li>
          <li v-if="releaseTypes.length === 0" class="text-sm opacity-70">
            No release types available.
          </li>
        </ul>

        <ul v-else-if="step === 'version'" class="space-y-2">
          <li v-for="v in versions.slice(0, 25)" :key="v.version">
            <button
                class="w-full rounded-theme border border-border-light dark:border-border-dark px-3 py-2 text-left transition-colors hover:bg-primary/10"
                @click="pickVersion(v)"
            >
              <div class="font-medium">
                {{ v.version }}
              </div>
              <div class="text-xs opacity-70">
                Published: {{ formatPublished(v.published) }}
              </div>
            </button>
          </li>
        </ul>

        <div v-else-if="step === 'confirm' && selectedVersion" class="space-y-4">
          <div>
            <div class="text-xs uppercase tracking-wider opacity-70">
              Selection
            </div>
            <div class="text-lg font-semibold">
              {{ product.name }} {{ selectedVersion.version }}
            </div>
            <div class="text-sm opacity-70">
              {{ selectedReleaseType?.name }}
            </div>
          </div>
          <p class="text-xs opacity-70">
            This is a one-time link. Do not distribute.
          </p>
        </div>
      </div>

      <footer class="flex items-center justify-between gap-2 border-t border-border-light dark:border-border-dark p-4">
        <SecondaryButton @click="back">
          <font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1" />
          {{ step === 'releaseType' ? 'Close' : 'Back' }}
        </SecondaryButton>
        <a
            v-if="step === 'confirm'"
            :href="downloadHref"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center rounded-theme bg-primary px-3 py-1.5 text-sm font-medium text-primary-text transition-colors hover:bg-primary-accent hover:text-primary-accent-text"
        >
          <font-awesome-icon :icon="['fas', 'download']" class="mr-1" />
          Download
        </a>
      </footer>
    </div>
  </div>
</template>
