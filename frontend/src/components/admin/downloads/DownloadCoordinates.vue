/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {DownloadEdit} from '~/api/adminDownloads'

/**
 * Where in Nexus a download's builds live.
 */
const edit = defineModel<DownloadEdit>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-4">
    <LabelledField :label="t('ui.downloadCoordinates.repository')"><TextInput v-model="edit.repository"/></LabelledField>
    <LabelledField :label="t('ui.downloadCoordinates.groupId')"><TextInput v-model="edit.groupId"/></LabelledField>
    <LabelledField :label="t('ui.downloadCoordinates.artifactId')"><TextInput v-model="edit.artifactId"/></LabelledField>
    <LabelledField :label="t('ui.downloadCoordinates.classifier')">
      <TextInput :model-value="edit.classifier ?? ''" @update:model-value="value => edit.classifier = value || null"/>
    </LabelledField>
  </div>
</template>
