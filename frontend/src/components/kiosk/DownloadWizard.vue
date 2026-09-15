/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {
    type DownloadTypeEntry,
    issueDownload,
    type IssuedDownload,
    type KioskProduct,
    listDownloadTypes,
    listReleaseTypes,
    listVersions,
    type ReleaseTypeEntry,
    type VersionEntry,
} from '~/api/kiosk'
import {formatDateTime, formatSize} from '~/util/format'

const props = defineProps<{
    product: KioskProduct | null
}>()

const emit = defineEmits<{
    close: []
}>()

type Step = 'releaseType' | 'version' | 'downloadType' | 'confirm'

const step = ref<Step>('releaseType')
const releaseTypes = ref<ReleaseTypeEntry[]>([])
const versions = ref<VersionEntry[]>([])
const downloadTypes = ref<DownloadTypeEntry[]>([])
const selectedReleaseType = ref<ReleaseTypeEntry | null>(null)
const selectedVersion = ref<VersionEntry | null>(null)
const issued = ref<IssuedDownload | null>(null)
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const needsLogin = ref(false)

/**
 * The trail across the top. A step nobody has a choice about is left out of it rather than shown
 * and skipped, so what the reader sees is the path they actually walked.
 *
 * <p>Every entry is disabled: it says where the wizard is, and going back is the back button's job.
 */
const crumbs = computed(() => [
    ...(releaseTypes.value.length > 1 ? [{key: 'releaseType', label: 'Release', disabled: true}] : []),
    {key: 'version', label: 'Version', disabled: true},
    ...(downloadTypes.value.length > 1 ? [{key: 'downloadType', label: 'Type', disabled: true}] : []),
    {key: 'confirm', label: 'Download', disabled: true},
])

const crumbIndex = computed(() => Math.max(0, crumbs.value.findIndex(crumb => crumb.key === step.value)))

/**
 * Turns whatever went wrong into the thing the reader can do about it.
 *
 * <p>Being told to sign in and being told the bot is not connected are different problems with
 * different remedies, and a single "something went wrong" hides both.
 */
function report(e: unknown, fallback: string) {
    const status = (e as {response?: {status?: number}}).response?.status
    needsLogin.value = status === 401
    if (status === 401) errorMessage.value = 'Sign in to download this product.'
    else if (status === 403) errorMessage.value = 'You do not hold a license for this product.'
    else if (status === 503) errorMessage.value = 'Downloads are unavailable: the Discord bot is not connected.'
    else errorMessage.value = fallback
}

watch(() => props.product, async next => {
    if (!next) return
    step.value = 'releaseType'
    selectedReleaseType.value = null
    selectedVersion.value = null
    downloadTypes.value = []
    versions.value = []
    issued.value = null
    errorMessage.value = null
    needsLogin.value = false
    loading.value = true
    try {
        releaseTypes.value = await listReleaseTypes(next.id)
        // Nothing to choose between is not a question worth asking, which is the shortcut the bot takes.
        if (releaseTypes.value.length === 1) await pickReleaseType(releaseTypes.value[0]!)
    } catch (e) {
        report(e, 'Could not load the release types.')
    } finally {
        loading.value = false
    }
}, {immediate: true})

async function pickReleaseType(releaseType: ReleaseTypeEntry) {
    if (!props.product) return
    selectedReleaseType.value = releaseType
    loading.value = true
    errorMessage.value = null
    try {
        versions.value = await listVersions(props.product.id, releaseType.id)
        step.value = 'version'
    } catch (e) {
        report(e, 'Could not load the versions.')
        step.value = 'version'
    } finally {
        loading.value = false
    }
}

async function pickVersion(version: VersionEntry) {
    if (!props.product) return
    selectedVersion.value = version
    loading.value = true
    errorMessage.value = null
    try {
        downloadTypes.value = version.downloadTypeIds.length > 1
            ? await listDownloadTypes(props.product.id, version.version)
            : []
        if (downloadTypes.value.length > 1) {
            step.value = 'downloadType'
            return
        }
        await pickDownloadType(version.downloadTypeIds[0]!)
    } catch (e) {
        report(e, 'Could not load the download types.')
    } finally {
        loading.value = false
    }
}

async function pickDownloadType(downloadTypeId: number) {
    if (!props.product || !selectedVersion.value) return
    loading.value = true
    errorMessage.value = null
    try {
        issued.value = await issueDownload(props.product.id, selectedVersion.value.version, downloadTypeId)
        step.value = 'confirm'
    } catch (e) {
        report(e, 'Could not prepare the download.')
    } finally {
        loading.value = false
    }
}

function back() {
    if (step.value === 'confirm') step.value = downloadTypes.value.length > 1 ? 'downloadType' : 'version'
    else if (step.value === 'downloadType') step.value = 'version'
    else if (step.value === 'version' && releaseTypes.value.length > 1) step.value = 'releaseType'
    else emit('close')
}
</script>

<template>
  <Modal v-if="product" :model-value="true" @update:model-value="emit('close')">
    <div class="space-y-4">
      <SubHeader>{{ product.name }}</SubHeader>
      <StepProgressBar :current-step="crumbIndex" :steps="crumbs" @update:current-step="() => undefined"/>

      <AsyncSection :error="errorMessage ?? undefined" :loading="loading" spinner-size="md">
        <template #error>
          <Alert variant="error">
            {{ errorMessage }}
            <NuxtLink v-if="needsLogin" class="ml-1 text-primary hover:underline" to="/login?next=/">Log in</NuxtLink>
          </Alert>
        </template>

        <ul v-if="step === 'releaseType'" class="space-y-2">
          <li v-for="entry in releaseTypes" :key="entry.id">
            <ChoiceButton :detail="entry.description ?? undefined" :title="entry.id" @click="pickReleaseType(entry)"/>
          </li>
          <EmptyHint v-if="!releaseTypes.length">Nothing is published for you to download yet.</EmptyHint>
        </ul>

        <ul v-else-if="step === 'version'" class="space-y-2">
          <li v-for="entry in versions" :key="entry.version">
            <ChoiceButton
                :detail="`Published ${formatDateTime(entry.publishedAt)}`"
                :title="entry.version"
                @click="pickVersion(entry)"
            />
          </li>
          <EmptyHint v-if="!versions.length">No version has been published for this release type.</EmptyHint>
        </ul>

        <ul v-else-if="step === 'downloadType'" class="space-y-2">
          <li v-for="entry in downloadTypes" :key="entry.id">
            <ChoiceButton
                :detail="entry.description ?? undefined"
                :title="entry.name"
                @click="pickDownloadType(entry.id)"
            />
          </li>
        </ul>

        <div v-else-if="issued" class="space-y-3">
          <div>
            <DetailLabel>File</DetailLabel>
            <div class="font-medium">{{ issued.filename }}</div>
            <MutedText v-if="issued.sizeBytes" tag="div">{{ formatSize(issued.sizeBytes) }}</MutedText>
          </div>
          <a
              :href="issued.url"
              class="inline-flex items-center rounded-theme bg-primary px-3 py-1.5 text-sm font-medium text-primary-text hover:bg-primary-accent"
          >
            <font-awesome-icon :icon="['fas', 'download']" class="mr-1"/>
            Download
          </a>
          <MutedText class="block" size="sm">This is a one-time link. Do not distribute.</MutedText>
        </div>
      </AsyncSection>

      <div class="flex justify-between">
        <SecondaryButton @click="back">
          <font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1"/>
          {{ step === 'releaseType' ? 'Close' : 'Back' }}
        </SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
