/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {getTrialStatus, type IssuedDownload, issueTrial, type KioskProductDetail, type TrialStatus} from '~/api/kiosk'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Trying a premium product once: one download of its newest stable build, as {@code /trial} gives on
 * Discord. Says why when it may not be taken, and how long until it may.
 */
const props = defineProps<{ product: KioskProductDetail, signedIn: boolean }>()

const {t} = useI18n()
const status = ref<TrialStatus | null>(null)
const issued = ref<IssuedDownload | null>(null)
const {busy, error, run} = useAdminAction(() => t('ui.productTrial.couldNotIssue'))

const days = computed(() => Math.ceil((status.value?.waitSeconds ?? 0) / 86400))
const minutes = computed(() => Math.ceil((status.value?.waitSeconds ?? 0) / 60))

async function take(typeId: number) {
  const link = await run(() => issueTrial(props.product.id, typeId))
  if (link) issued.value = link
}

onMounted(async () => {
  if (props.signedIn) status.value = await getTrialStatus(props.product.id).catch(() => null)
})
</script>

<template>
  <section id="trial" class="space-y-3 border border-border-light p-4 dark:border-border-dark">
    <CardHeader>{{ t('ui.productTrial.title') }}</CardHeader>
    <MutedText size="sm" tag="p">{{ t('ui.productTrial.explain') }}</MutedText>
    <p v-if="!signedIn" class="text-sm">
      <NuxtLink :to="{path: '/login', query: {next: `/products/${product.id}?trial=1`}}" class="text-primary underline">{{ t('ui.productTrial.signIn') }}</NuxtLink>
    </p>
    <template v-else-if="issued">
      <p class="text-sm">{{ t('ui.productTrial.issued') }}</p>
      <a :download="issued.filename" :href="issued.url" class="font-data text-primary underline">{{ issued.filename }}</a>
    </template>
    <template v-else-if="status">
      <ul v-if="status.reason === 'ELIGIBLE'" class="space-y-2">
        <li v-for="download in status.downloads" :key="download.typeId" class="flex items-center gap-3">
          <span class="font-medium">{{ download.name }}</span>
          <MutedText class="font-data" size="xs">{{ download.version }}</MutedText>
          <PrimaryButton :disabled="busy" class="ml-auto" @click="take(download.typeId)">{{ t('ui.productTrial.take') }}</PrimaryButton>
        </li>
      </ul>
      <MutedText v-else size="sm" tag="p">{{ t(`ui.productTrial.reason.${status.reason}`, {days, minutes}) }}</MutedText>
    </template>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </section>
</template>
