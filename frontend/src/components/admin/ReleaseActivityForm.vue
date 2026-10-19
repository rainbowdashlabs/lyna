/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {type ReleaseActivity, type ReleaseWebhook, setReleaseActivity} from '~/api/admin'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Where a product's webhook posts the repository's pushes, pull requests and issues, and which of
 * them. Kept apart from the release channel, which buyers read.
 */
const props = defineProps<{ guildId: string, productId: number, webhook: ReleaseWebhook }>()

const {t} = useI18n()
const channelId = ref(props.webhook.activityChannelId ?? '')
const activity = ref<ReleaseActivity>({...props.webhook.activity})
const {busy, done, error, run} = useAdminAction(() => t('ui.releaseActivityForm.couldNotSave'))

const SWITCHES: (keyof ReleaseActivity)[] = ['push', 'prOpened', 'prMerged', 'issueOpened', 'issueClosed']

const save = () => run(() => setReleaseActivity(props.guildId, props.productId, channelId.value.trim() || null, activity.value))
</script>

<template>
  <form class="space-y-3" @submit.prevent="save">
    <SectionLabel>{{ t('ui.releaseActivityForm.title') }}</SectionLabel>
    <MutedText class="block" size="xs">{{ t('ui.releaseActivityForm.explain') }}</MutedText>
    <LabelledField :label="t('ui.releaseActivityForm.channelId')">
      <ChannelPicker v-model="channelId" :guild-id="guildId" optional purpose="posting"/>
    </LabelledField>
    <div class="flex flex-wrap gap-4">
      <label v-for="key in SWITCHES" :key="key" class="flex items-center gap-2 text-sm">
        <CheckboxInput v-model="activity[key]"/> {{ t(`ui.releaseActivityForm.${key}`) }}
      </label>
    </div>
    <div class="flex items-center gap-3">
      <PrimaryButton :disabled="busy" type="submit">{{ t('ui.releaseActivityForm.save') }}</PrimaryButton>
      <MutedText v-if="done" size="sm">{{ t('ui.releaseActivityForm.saved') }}</MutedText>
    </div>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </form>
</template>
